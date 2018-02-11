package frc.team5190.robot.auto

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.command.Command
import frc.team5190.robot.drive.DriveSubsystem
import frc.team5190.robot.util.DriveConstants
import frc.team5190.robot.util.Maths
import kotlin.math.absoluteValue

class MotionMagicCommand(feet: Double) : Command() {

    private val setPoint = Maths.feetToNativeUnits(feet, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS).toDouble()

    init {
        requires(DriveSubsystem)
    }

    override fun initialize() {
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
        (it.sensorCollection.quadraturePosition - setPoint).absoluteValue < Maths.feetToNativeUnits(0.1, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS).toDouble() &&
                it.sensorCollection.quadratureVelocity < 100
    }

}