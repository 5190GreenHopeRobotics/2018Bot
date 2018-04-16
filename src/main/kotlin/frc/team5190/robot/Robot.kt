/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.*
import edu.wpi.first.wpilibj.command.CommandGroup
import edu.wpi.first.wpilibj.command.Scheduler
import edu.wpi.first.wpilibj.livewindow.LiveWindow
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import frc.team5190.robot.arm.ArmSubsystem
import frc.team5190.robot.auto.*
import frc.team5190.robot.climb.ClimbSubsystem
import frc.team5190.robot.climb.IdleClimbCommand
import frc.team5190.robot.drive.DriveSubsystem
import frc.team5190.robot.drive.StraightDriveCommand
import frc.team5190.robot.elevator.ElevatorSubsystem
import frc.team5190.robot.intake.IntakeSubsystem
import frc.team5190.robot.sensors.*
import frc.team5190.robot.util.commandGroup
import openrio.powerup.MatchData

class Robot : IterativeRobot() {

    companion object {
        var INSTANCE: Robot? = null
    }

    init {
        INSTANCE = this
    }


    // Autonomous variables
    private val sideChooser = SendableChooser<StartingPositions>()

    private val crossAutoChooser = SendableChooser<AutoModes>()
    private val sameSideAutoChooser = SendableChooser<AutoModes>()

    var switchSide = MatchData.OwnedSide.UNKNOWN
    var scaleSide = MatchData.OwnedSide.UNKNOWN

    var sideChooserSelected = StartingPositions.LEFT
    var sameSideAutoSelected = AutoModes.FULL
    var crossAutoSelected = AutoModes.FULL

    private var autonomousRoutine: CommandGroup? = null
    private var hasRunAuto = false

    var fmsDataReceived = false



    override fun robotInit() {
        // https://www.chiefdelphi.com/forums/showthread.php?p=1724798
        LiveWindow.disableAllTelemetry()

        // Initialize subsystems and sensors
        DriveSubsystem
        IntakeSubsystem
        ElevatorSubsystem
        ClimbSubsystem
        ArmSubsystem

        Pathreader
        Canifier
        Lidar
        Pigeon
        LEDs

        // Start camera stream
        CameraServer.getInstance().startAutomaticCapture().apply {
            setResolution(640, 480)
            setFPS(20)
        }

        // Autonomous modes on Dashboard
        StartingPositions.values().forEach { sideChooser.addObject(it.name.toLowerCase().capitalize(), it) }

        AutoModes.values().forEach {
            sameSideAutoChooser.addObject(it.name.toLowerCase().capitalize() + " (${it.numCubes})", it)
            crossAutoChooser.addObject(it.name.toLowerCase().capitalize() + " (${it.numCubes})", it)
        }

        SmartDashboard.putData("Starting Position", sideChooser)

        SmartDashboard.putData("Cross Scale Mode", crossAutoChooser)
        SmartDashboard.putData("Same Side Scale Mode", sameSideAutoChooser)

        // Reset subsystems for autonomous
        IntakeSubsystem.enableVoltageCompensation()
        DriveSubsystem.autoReset()
    }

    override fun robotPeriodic() {
        // Logging
        SmartDashboard.putNumber("Pigeon Corrected Angle", Pigeon.correctedAngle)


        // Receives game data from the FMS and generates autonomous routine
        if (!INSTANCE!!.isOperatorControl && autonomousRoutine?.isRunning != true) {
            try {
                if (sideChooser.selected != sideChooserSelected ||
                        sameSideAutoChooser.selected != sameSideAutoSelected ||
                        crossAutoChooser.selected != crossAutoSelected ||
                        MatchData.getOwnedSide(MatchData.GameFeature.SWITCH_NEAR) != switchSide ||
                        MatchData.getOwnedSide(MatchData.GameFeature.SCALE) != scaleSide ||
                        hasRunAuto) {

                    DriveSubsystem.autoReset()

                    sideChooserSelected = sideChooser.selected
                    sameSideAutoSelected = sameSideAutoChooser.selected
                    crossAutoSelected = crossAutoChooser.selected

                    switchSide = MatchData.getOwnedSide(MatchData.GameFeature.SWITCH_NEAR)
                    scaleSide = MatchData.getOwnedSide(MatchData.GameFeature.SCALE)

                    fmsDataReceived = switchSide != MatchData.OwnedSide.UNKNOWN && scaleSide != MatchData.OwnedSide.UNKNOWN

                    println("Received Game Specific Data: ${DriverStation.getInstance().gameSpecificMessage}")

                    autonomousRoutine = AutoHelper.getAuto(sideChooserSelected, switchSide, scaleSide, sameSideAutoSelected, crossAutoSelected)
                }
            } catch (ignored: Exception) {
            }
        }
        Scheduler.getInstance().run()
    }

    override fun autonomousInit() {
        // Reset gyro
        Pigeon.reset()
        Pigeon.angleOffset = if (sideChooserSelected == StartingPositions.CENTER) 0.0 else 180.0
    }

    override fun autonomousPeriodic() {
        // Runs the autonomous routine once data has been received
        if (autonomousRoutine?.isRunning == false && !hasRunAuto && switchSide != MatchData.OwnedSide.UNKNOWN && scaleSide != MatchData.OwnedSide.UNKNOWN) {
            autonomousRoutine?.start()
            hasRunAuto = true
        }

        // Runs baseline auto if for some reason FMS data was not received
        if (Timer.getMatchTime() < 5.0 && !hasRunAuto) {
            commandGroup {
                addSequential(StraightDriveCommand(12.0 * if (sideChooserSelected == StartingPositions.CENTER) 1 else -1))
            }.start()
        }
    }

    override fun disabledInit() {
        // Clean up from climbing
        IdleClimbCommand().start()
        ClimbSubsystem.climbState = false
    }

    override fun teleopInit() {
        // Lock elevator in place
        ElevatorSubsystem.set(ControlMode.MotionMagic, ElevatorSubsystem.currentPosition.toDouble())
        ArmSubsystem.set(ControlMode.MotionMagic, ArmSubsystem.currentPosition.toDouble())
        IntakeSubsystem.disableVoltageCompensation()


        // Clean up from autonomous
        autonomousRoutine?.cancel()
        hasRunAuto = true

        DriveSubsystem.teleopReset()
        DriveSubsystem.controller = "Xbox"
    }
}
