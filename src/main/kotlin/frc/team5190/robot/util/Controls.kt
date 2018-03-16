/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.util

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.GenericHID
import frc.team5190.robot.*
import frc.team5190.robot.arm.ArmPosition
import frc.team5190.robot.arm.ArmSubsystem
import frc.team5190.robot.arm.AutoArmCommand
import frc.team5190.robot.climb.BalanceWinchCommand
import frc.team5190.robot.climb.ClimbSubsystem
import frc.team5190.robot.climb.DeployHookCommand
import frc.team5190.robot.climb.WinchCommand
import frc.team5190.robot.drive.DriveMode
import frc.team5190.robot.drive.DriveSubsystem
import frc.team5190.robot.drive.Gear
import frc.team5190.robot.elevator.*
import frc.team5190.robot.intake.IntakeCommand
import frc.team5190.robot.intake.IntakeDirection
import frc.team5190.robot.intake.IntakeSubsystem
import kotlin.math.absoluteValue

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

        if (Robot.INSTANCE!!.isOperatorControl) {
            if (MainXbox.aButton)
                DriveSubsystem.falconDrive.gear = Gear.LOW
            else {
                // Auto Shift Logic

                val speed = DriveSubsystem.falconDrive.allMasters.map { Maths.nativeUnitsPer100MsToFeetPerSecond(it.getSelectedSensorVelocity(0).absoluteValue) }.average()
                when {
                    speed > DriveConstants.AUTO_SHIFT_HIGH_THRESHOLD -> DriveSubsystem.falconDrive.gear = Gear.HIGH
                    speed < DriveConstants.AUTO_SHIFT_LOW_THRESHOLD -> DriveSubsystem.falconDrive.gear = Gear.LOW
                }
            }
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
                val motorOut = 0.55
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
                val motorOut = -0.3
                ElevatorSubsystem.set(ControlMode.PercentOutput, motorOut)
            }
            MainXbox.getBumperReleased(GenericHID.Hand.kRight) && !ClimbSubsystem.climbState -> {
                ElevatorSubsystem.peakElevatorOutput = ElevatorConstants.IDLE_PEAK_OUT
                ElevatorSubsystem.set(ControlMode.MotionMagic, ElevatorSubsystem.currentPosition - 500.0)
            }
        }

        if (ClimbSubsystem.climbState) return

        val pov = MainXbox.pov
        when (pov) {
        // Up - Scale
            0 -> ElevatorPresetCommand(ElevatorPreset.SCALE)
        // Right - Switch
            90 -> ElevatorPresetCommand(ElevatorPreset.SWITCH)
        // Down - Intake
            180 -> ElevatorPresetCommand(ElevatorPreset.INTAKE)
        // Left - Scale Backwards
            270 -> ElevatorPresetCommand(ElevatorPreset.BEHIND)
            else -> null
        }?.let {
            ElevatorSubsystem.currentCommandGroup?.cancel()
            ElevatorSubsystem.currentCommandGroup = it
            it.start()
        }
    }

    fun climbSubsystem() {
        if (ClimbSubsystem.climbState) {
            if (MainXbox.aButtonPressed) {
                BalanceWinchCommand().start()
            }
            if (MainXbox.aButtonReleased) {
                WinchCommand().start()
            }
        }

        if (MainXbox.backButtonPressed) {
            ClimbSubsystem.climbState = true
            if (ClimbSubsystem.currentCommand !is WinchCommand) {
                commandGroup {
                    addSequential(commandGroup {
                        addParallel(commandGroup {
                            addSequential(AutoArmCommand(ArmPosition.ALL_UP), 2.0)
                            addSequential(DeployHookCommand())
                        })
                        addParallel(AutoElevatorCommand(ElevatorPosition.INTAKE))
                    })
                    addSequential(WinchCommand())
                }.start()
            } else {
                ClimbSubsystem.currentCommand?.cancel()
                // Just in case the Hook doesn't deploy (idk this happened once before)
                commandGroup {
                    addSequential(DeployHookCommand())
                    addSequential(WinchCommand())
                }.start()
            }
        }

        if (MainXbox.startButtonPressed) {
            ClimbSubsystem.climbState = false
            ClimbSubsystem.currentCommand?.cancel()
        }
    }

    fun winchSubsystem() {
        if (!MainXbox.getBumper(GenericHID.Hand.kLeft)) ClimbSubsystem.frontWinchMotor.set(ControlMode.PercentOutput, MainXbox.getTriggerAxis(GenericHID.Hand.kLeft) * ClimbConstants.PEAK_OUTPUT)
        if (!MainXbox.getBumper(GenericHID.Hand.kRight)) ClimbSubsystem.backWinchMotor.set(ControlMode.PercentOutput, MainXbox.getTriggerAxis(GenericHID.Hand.kRight) * ClimbConstants.PEAK_OUTPUT)

        if (MainXbox.getBumper(GenericHID.Hand.kLeft)) ClimbSubsystem.frontWinchMotor.set(ControlMode.PercentOutput, -0.3)
        if (MainXbox.getBumper(GenericHID.Hand.kRight)) ClimbSubsystem.backWinchMotor.set(ControlMode.PercentOutput, -0.3)
    }
}