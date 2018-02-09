/**
 * FRC Team 5190
 * Programming Team
 */

package frc.team5190.robot

import edu.wpi.first.wpilibj.IterativeRobot
import edu.wpi.first.wpilibj.command.Scheduler
import edu.wpi.first.wpilibj.livewindow.LiveWindow
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import frc.team5190.robot.arm.*
import frc.team5190.robot.auto.*
import frc.team5190.robot.drive.DriveSubsystem
import frc.team5190.robot.drive.Gear
import frc.team5190.robot.elevator.ElevatorSubsystem
import frc.team5190.robot.elevator.ResetElevatorCommand
import frc.team5190.robot.intake.IntakeSubsystem
import frc.team5190.robot.sensors.NavX
import frc.team5190.robot.util.Maths
import frc.team5190.robot.util.commandGroup
import frc.team5190.robot.vision.VisionSubsystem
import openrio.powerup.MatchData

/**
 * Main robot class
 */
class Robot : IterativeRobot() {

    companion object {
        var INSTANCE: Robot? = null
    }

    init {
        INSTANCE = this
    }

    // Shows a drop down on dashboard that allows us to select which mode we want
    private val sideChooser = SendableChooser<StartingPositions>()

    // Shows a dropdown of the controllers that weill be used.
    private val controllerChooser = SendableChooser<String>()

    // Variable that stores which side of the switch to go to.
    private var switchSide = MatchData.OwnedSide.UNKNOWN

    // Variable that stores which side of the scale to go to.
    private var scaleSide = MatchData.OwnedSide.UNKNOWN


    /**
     * Executed when robot code first launches and is ready to be initialized.
     */
    override fun robotInit() {
        // https://www.chiefdelphi.com/forums/showthread.php?p=1724798
        LiveWindow.disableAllTelemetry()

        DriveSubsystem
        VisionSubsystem
        IntakeSubsystem
        ElevatorSubsystem
        ArmSubsystem
        NavX

        StartingPositions.values().forEach { sideChooser.addObject(it.name.toLowerCase().capitalize(), it) }

        controllerChooser.addObject("Xbox", "Xbox")
        controllerChooser.addObject("Bongo", "Bongo")

        controllerChooser.addDefault("Xbox", "Xbox")

        SmartDashboard.putData("Starting Position", sideChooser)
        SmartDashboard.putData("Controller", controllerChooser)
    }

    /**
     * Executed periodically.
     */
    override fun robotPeriodic() {

        // Debug information
        SmartDashboard.putNumber("Left Motor RPM", Maths.nativeUnitsPer100MsToRPM(DriveSubsystem.falconDrive.leftMaster.getSelectedSensorVelocity(0)))
        SmartDashboard.putNumber("Right Motor RPM", Maths.nativeUnitsPer100MsToRPM(DriveSubsystem.falconDrive.rightMaster.getSelectedSensorVelocity(0)))

        SmartDashboard.putNumber("Left Encoder Position", DriveSubsystem.falconDrive.leftEncoderPosition.toDouble())
        SmartDashboard.putNumber("Right Encoder Position", DriveSubsystem.falconDrive.rightEncoderPosition.toDouble())

        SmartDashboard.putNumber("Left Encoder to Feet", Maths.nativeUnitsToFeet(DriveSubsystem.falconDrive.leftEncoderPosition))
        SmartDashboard.putNumber("Right Encoder to Feet", Maths.nativeUnitsToFeet(DriveSubsystem.falconDrive.rightEncoderPosition))

        SmartDashboard.putNumber("Elevator Encoder Position", ElevatorSubsystem.currentPosition.toDouble())
        SmartDashboard.putNumber("Elevator Inches Position", ElevatorSubsystem.nativeUnitsToInches(ElevatorSubsystem.currentPosition))

        SmartDashboard.putNumber("Arm Encoder Position", ArmSubsystem.currentPosition.toDouble())

        SmartDashboard.putNumber("Elevator Motor Amperage", ElevatorSubsystem.motorAmperage)
        SmartDashboard.putNumber("Left Motor Amperage", DriveSubsystem.leftMotorAmperage)
        SmartDashboard.putNumber("Right Motor Amerpage", DriveSubsystem.rightMotorAmperage)

        SmartDashboard.putNumber("Arm Motor Amperage", ArmSubsystem.armMotorAmperage)
        SmartDashboard.putNumber("Intake Motor Amperage", IntakeSubsystem.intakeMotorAmperage)

        SmartDashboard.putData("Elevator Subsystem", ElevatorSubsystem)
        SmartDashboard.putData("Drive Subsystem", DriveSubsystem)
        SmartDashboard.putData("Arm Subsystem", ArmSubsystem)
        SmartDashboard.putData("Intake Subsystem", IntakeSubsystem)
        SmartDashboard.putData("Vision Subsystem", VisionSubsystem)
        SmartDashboard.putData("Gyro", NavX)

        Scheduler.getInstance().run()
    }

    /**
     * Executed when autonomous is initialized
     */
    override fun autonomousInit() {
        ResetElevatorCommand().start()

        DriveSubsystem.autoReset()
        DriveSubsystem.falconDrive.gear = Gear.HIGH

        this.pollForFMSData()

        NavX.reset()

//        AutoHelper.getCommandGroupFromData(sideChooser.selected?: StartingPositions.CENTER, switchSide, scaleSide).start()

        commandGroup {
            this.addSequential(frc.team5190.robot.vision.FindCubeCommand())
            this.addSequential(frc.team5190.robot.util.commandGroup {
                this.addParallel(MotionMagicCommand((VisionSubsystem.tgtRange_in - 5) / 12))
                this.addParallel(frc.team5190.robot.intake.IntakeCommand(frc.team5190.robot.intake.IntakeDirection.IN, true))
            })
            this.addSequential(frc.team5190.robot.intake.IntakeHoldCommand(), 0.001)
            this.addSequential(frc.team5190.robot.auto.MotionProfileCommand(Paths.CS_STRAIGHT, true))
//            this.addSequential(frc.team5190.robot.auto.MotionMagicCommand(3.0, true))

        }.start()
    }

    /**
     * Executed once when robot is disabled.
     */
    override fun disabledInit() {
        this.pollForFMSData()
    }

    /**
     * Executed when teleop is initialized
     */
    override fun teleopInit() {
        commandGroup {
            if (ArmSubsystem.currentPosition < ArmPosition.DOWN.ticks)
                addSequential(AutoArmCommand(ArmPosition.DOWN))
            addSequential(ResetElevatorCommand())
        }.start()

        DriveSubsystem.currentCommand?.cancel()

        DriveSubsystem.teleopReset()
        DriveSubsystem.controller = controllerChooser.selected ?: "Xbox"
    }

    private fun pollForFMSData() {
        if (switchSide == MatchData.OwnedSide.UNKNOWN) switchSide = MatchData.getOwnedSide(MatchData.GameFeature.SWITCH_NEAR)
        if (scaleSide == MatchData.OwnedSide.UNKNOWN) scaleSide = MatchData.getOwnedSide(MatchData.GameFeature.SCALE)
    }
}
