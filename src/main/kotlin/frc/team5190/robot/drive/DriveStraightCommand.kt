package frc.team5190.robot.drive

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.command.Command
import frc.team5190.robot.util.Hardware
import frc.team5190.robot.util.Maths
import kotlin.math.absoluteValue

class DriveStraightCommand(feet: Double, val isReversed: Boolean = false) : Command() {

    private val setPoint = Maths.feetToNativeUnits(feet, Hardware.NATIVE_UNITS_PER_ROTATION, Hardware.WHEEL_RADIUS).toDouble()

    init {
        requires(DriveSubsystem)
    }

    override fun initialize() {

        DriveSubsystem.falconDrive.leftMotors.forEach {
            it.inverted = isReversed
            it.setSensorPhase(true)
        }
        DriveSubsystem.falconDrive.rightMotors.forEach {
            it.inverted = !isReversed
            it.setSensorPhase(true)
        }

        DriveSubsystem.falconDrive.allMasters.forEach {
            it.sensorCollection.setQuadraturePosition(0, 10)
            it.set(ControlMode.MotionMagic, setPoint)
        }
    }

    override fun end() {
        DriveSubsystem.autoReset()
    }

    override fun execute() {
        DriveSubsystem.falconDrive.feedSafety()
    }

    override fun isFinished() =  DriveSubsystem.falconDrive.allMasters.any {
        (it.sensorCollection.quadraturePosition - setPoint).absoluteValue < Maths.feetToNativeUnits(0.1, Hardware.NATIVE_UNITS_PER_ROTATION, Hardware.WHEEL_RADIUS).toDouble() &&
                it.sensorCollection.quadratureVelocity < 100
    }

}