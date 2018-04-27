/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.drive

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.command.Command
import frc.team5190.robot.sensors.Pigeon
import frc.team5190.robot.util.DriveConstants
import frc.team5190.robot.util.Maths
import jaci.pathfinder.Pathfinder

class ArcDriveCommand(val feet: Double, val angle: Double,
                      private val cruiseVel: Double = DriveConstants.MOTION_MAGIC_CRUISE,
                      private val accel: Double = DriveConstants.MOTION_MAGIC_ACCEL) : Command() {

    // Setpoint in Native Units
    private var setPoint = Maths.feetToNativeUnits(feet, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS).toDouble()

    init {
        requires(DriveSubsystem)
    }

    override fun initialize() {
        // Calculuations
        val currentAngle = Pigeon.correctedAngle

        val angleDelta = Math.toRadians(Pathfinder.boundHalfDegrees(angle - currentAngle))

        val radius = feet / Math.sin(angleDelta) * Math.sin((Math.PI - angleDelta) / 2.0)
        val arcLength = radius * angleDelta

        val wheelBase = DriveConstants.DRIVE_BASE_WIDTH / 12.0 / 2.0
        val leftScale = ((radius - wheelBase) * angleDelta) / arcLength
        val rightScale = ((radius + wheelBase) * angleDelta) / arcLength

        // Motion Magic setpt
        with(DriveSubsystem.falconDrive.leftMaster) {
            configMotionCruiseVelocity(Maths.feetPerSecondToNativeUnitsPer100Ms(leftScale * cruiseVel, DriveConstants.WHEEL_RADIUS, DriveConstants.SENSOR_UNITS_PER_ROTATION).toInt(), 10)
            configMotionAcceleration(Maths.feetPerSecondToNativeUnitsPer100Ms(leftScale * accel, DriveConstants.WHEEL_RADIUS, DriveConstants.SENSOR_UNITS_PER_ROTATION).toInt(), 10)

            sensorCollection.setQuadraturePosition(0, 10)
            set(ControlMode.MotionMagic, setPoint * leftScale)
        }
        with(DriveSubsystem.falconDrive.rightMaster) {
            configMotionCruiseVelocity(Maths.feetPerSecondToNativeUnitsPer100Ms(rightScale * cruiseVel, DriveConstants.WHEEL_RADIUS, DriveConstants.SENSOR_UNITS_PER_ROTATION).toInt(), 10)
            configMotionAcceleration(Maths.feetPerSecondToNativeUnitsPer100Ms(rightScale * accel, DriveConstants.WHEEL_RADIUS, DriveConstants.SENSOR_UNITS_PER_ROTATION).toInt(), 10)

            sensorCollection.setQuadraturePosition(0, 10)
            set(ControlMode.MotionMagic, setPoint * rightScale)
        }
    }

    // Called when command ends
    override fun end() {
        DriveSubsystem.falconDrive.leftMotors.forEach { it.inverted = false }
        DriveSubsystem.falconDrive.rightMotors.forEach { it.inverted = true }

        DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, 0.0, 0.0)
    }

    // Command never ends until timed out because math is not fully implemented (it is but we are lazy)
    override fun isFinished() = false

}