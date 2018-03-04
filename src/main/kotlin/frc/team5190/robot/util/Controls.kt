/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.util

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.GenericHID
import frc.team5190.robot.*
import frc.team5190.robot.arm.*
import frc.team5190.robot.climb.*
import frc.team5190.robot.drive.*
import frc.team5190.robot.elevator.*
import frc.team5190.robot.intake.*

object Controls {

    private val driveMode = ControlMode.PercentOutput

    private var teleIntake = false
    private var triggerState = false

    fun driveSubsystem() {
        when {
            DriveSubsystem.controlMode == DriveMode.ARCADE -> DriveSubsystem.falconDrive.arcadeDrive(-MainXbox.getLeftY(), MainXbox.getLeftX())
            DriveSubsystem.controlMode == DriveMode.CURVE -> DriveSubsystem.falconDrive.curvatureDrive(driveMode, -MainXbox.getLeftY(), MainXbox.getLeftX(), MainXbox.xButton)
            DriveSubsystem.controlMode == DriveMode.TANK -> when {
                DriveSubsystem.controller == "Bongo" -> DriveSubsystem.falconDrive.tankDrive(driveMode, Bongos.getLeftBongoSpeed(), Bongos.getRightBongoSpeed())
                else -> DriveSubsystem.falconDrive.tankDrive(driveMode, -MainXbox.getLeftY(), -MainXbox.getRightY())
            }
        }

        DriveSubsystem.falconDrive.gear = when {
            MainXbox.aButton -> Gear.LOW
            else -> Gear.HIGH
        }
    }

    fun intakeSubsystem() {

        val climbState = ClimbSubsystem.climbState

        when {
            MainXbox.getBumper(GenericHID.Hand.kLeft) && !climbState -> {
                IntakeCommand(IntakeDirection.IN).start()
                teleIntake = true
            }
            MainXbox.getTriggerAxis(GenericHID.Hand.kLeft) > 0.5 && !climbState -> {
                IntakeCommand(IntakeDirection.OUT).start()
                teleIntake = true
            }
            teleIntake -> {
                IntakeSubsystem.currentCommand?.cancel()
                teleIntake = false
            }
        }
    }

    fun armSubsystem() {
        when {
            MainXbox.yButton -> ArmSubsystem.set(ControlMode.PercentOutput, 0.3)
            MainXbox.bButton -> ArmSubsystem.set(ControlMode.PercentOutput, -0.2)

            MainXbox.yButtonReleased -> ArmSubsystem.set(ControlMode.MotionMagic, ArmSubsystem.currentPosition.toDouble() + 50)
            MainXbox.bButtonReleased -> ArmSubsystem.set(ControlMode.MotionMagic, ArmSubsystem.currentPosition.toDouble() - 50)
        }
    }

    fun elevatorSubsystem() {
        when {
            MainXbox.getTriggerPressed(GenericHID.Hand.kRight) && !ClimbSubsystem.climbState -> {
                val motorOut = 0.5
                ElevatorSubsystem.peakElevatorOutput = ElevatorConstants.ACTIVE_PEAK_OUT
                ElevatorSubsystem.set(ControlMode.PercentOutput, motorOut)
                triggerState = true
            }
            triggerState -> {
                ElevatorSubsystem.peakElevatorOutput = ElevatorConstants.IDLE_PEAK_OUT
                ElevatorSubsystem.set(ControlMode.MotionMagic, ElevatorSubsystem.currentPosition + 500.0)
                triggerState = false
            }
        }
        when {
            MainXbox.getBumper(GenericHID.Hand.kRight) && !ClimbSubsystem.climbState -> {
                ElevatorSubsystem.peakElevatorOutput = ElevatorConstants.ACTIVE_PEAK_OUT
                val motorOut = -0.1
                ElevatorSubsystem.set(ControlMode.PercentOutput, motorOut)
            }
            MainXbox.getBumperReleased(GenericHID.Hand.kRight) && !ClimbSubsystem.climbState -> {
                ElevatorSubsystem.peakElevatorOutput = ElevatorConstants.IDLE_PEAK_OUT
                ElevatorSubsystem.set(ControlMode.MotionMagic, ElevatorSubsystem.currentPosition - 500.0)
            }
        }

        val pov = MainXbox.pov
        when (pov) {
        // Up - Scale
            0 -> commandGroup {
                addParallel(AutoArmCommand(ArmPosition.MIDDLE))
                addParallel(AutoElevatorCommand(ElevatorPosition.SCALE_HIGH))
            }
        // Right - Switch
            90 -> commandGroup {
                //
                addParallel(AutoArmCommand(ArmPosition.MIDDLE))
                addParallel(commandGroup {
                    addSequential(object : AutoElevatorCommand(ElevatorPosition.FIRST_STAGE) {
                        override fun isFinished() = ArmSubsystem.currentPosition < ArmPosition.UP.ticks + 100
                    })
                    addSequential(AutoElevatorCommand(ElevatorPosition.SWITCH))
                })
            }
        // Down - Intake
            180 -> commandGroup {
                addParallel(AutoArmCommand(ArmPosition.DOWN))
                addParallel(commandGroup {
                    addSequential(object : AutoElevatorCommand(ElevatorPosition.FIRST_STAGE) {
                        override fun isFinished() = ArmSubsystem.currentPosition < ArmPosition.UP.ticks + 100
                    })
                    addSequential(AutoElevatorCommand(ElevatorPosition.INTAKE))
                })
            }
        // Left - Scale Backwards
            270 -> commandGroup {
                addParallel(AutoElevatorCommand(ElevatorPosition.SCALE))
                addParallel(commandGroup {
                    addSequential(object : AutoArmCommand(ArmPosition.UP) {
                        override fun isFinished() = ElevatorSubsystem.currentPosition > ElevatorPosition.FIRST_STAGE.ticks + 100
                    })
                    addSequential(AutoArmCommand(ArmPosition.BEHIND))
                })
            }
            else -> null
        }?.let {
            ElevatorSubsystem.currentCommandGroup?.cancel()
            ElevatorSubsystem.currentCommandGroup = it
            it.start()
        }
    }

    fun climbSubsystem() {
        if (MainXbox.backButtonPressed) {
            ClimbSubsystem.climbState = true
            if (ClimbSubsystem.currentCommand !is WinchCommand) {
                commandGroup {
                    addSequential(commandGroup {
                        addParallel(AutoArmCommand(ArmPosition.ALL_UP))
                        addParallel(AutoElevatorCommand(ElevatorPosition.INTAKE))
                    })
                    addSequential(DeployHookCommand())
                    addSequential(WinchCommand())
                }.start()
            } else return
        }
        
        if (MainXbox.startButtonPressed) {
            ClimbSubsystem.climbState = false
            ClimbSubsystem.currentCommand?.cancel()
        }
    }

    fun winchSubsystem() {
        if (!MainXbox.getBumper(GenericHID.Hand.kLeft)) ClimbSubsystem.frontWinchMotor.set(ControlMode.PercentOutput, MainXbox.getTriggerAxis(GenericHID.Hand.kLeft))
        if (!MainXbox.getBumper(GenericHID.Hand.kRight)) ClimbSubsystem.backWinchMotor.set(ControlMode.PercentOutput, MainXbox.getTriggerAxis(GenericHID.Hand.kRight))

        if (MainXbox.getBumper(GenericHID.Hand.kLeft)) ClimbSubsystem.frontWinchMotor.set(ControlMode.PercentOutput, -0.3)
        if (MainXbox.getBumper(GenericHID.Hand.kRight)) ClimbSubsystem.backWinchMotor.set(ControlMode.PercentOutput, -0.3)
    }
}