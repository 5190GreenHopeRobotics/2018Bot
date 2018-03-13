/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.auto

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.Notifier
import edu.wpi.first.wpilibj.Timer
import edu.wpi.first.wpilibj.command.Command
import frc.team5190.robot.drive.DriveSubsystem
import frc.team5190.robot.sensors.NavX
import frc.team5190.robot.util.DriveConstants
import frc.team5190.robot.util.Maths
import jaci.pathfinder.Pathfinder
import jaci.pathfinder.followers.EncoderFollower
import java.io.File

class MotionProfileCommand(folder: String, file: String, isReversed: Boolean, isMirrored: Boolean) : Command() {

    private val leftPath = Pathfinder.readFromCSV(File("/home/lvuser/paths/$folder/$file Left Detailed.csv"))
    private val rightPath = Pathfinder.readFromCSV(File("/home/lvuser/paths/$folder/$file Right Detailed.csv"))

    private val leftEncoderFollower: EncoderFollower
    private val rightEncoderFollower: EncoderFollower

    val mpTime
        get() = leftPath.length() * DriveConstants.MOTION_DT

    private val notifier: Notifier

    private var startTime: Double? = null

    init {
        requires(DriveSubsystem)

        val leftTrajectory = if (isMirrored) rightPath else leftPath
        val rightTrajectory = if (isMirrored) leftPath else rightPath

        DriveSubsystem.falconDrive.leftMotors.forEach {
            it.inverted = isReversed
            it.setSensorPhase(!DriveConstants.IS_RACE_ROBOT)
        }
        DriveSubsystem.falconDrive.rightMotors.forEach {
            it.inverted = !isReversed
            it.setSensorPhase(!DriveConstants.IS_RACE_ROBOT)
        }

        leftEncoderFollower = EncoderFollower(if (isReversed) rightTrajectory else leftTrajectory).apply {
            configureEncoder(DriveSubsystem.falconDrive.leftEncoderPosition, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(2.0, 0.0, 0.0, 1 / Maths.rpmToFeetPerSecond(DriveConstants.MAX_RPM_HIGH, DriveConstants.WHEEL_RADIUS), 0.0)
        }

        rightEncoderFollower = EncoderFollower(if (isReversed) leftTrajectory else rightTrajectory).apply {
            configureEncoder(DriveSubsystem.falconDrive.rightEncoderPosition, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(2.0, 0.0, 0.0, 1 / Maths.rpmToFeetPerSecond(DriveConstants.MAX_RPM_HIGH, DriveConstants.WHEEL_RADIUS), 0.0)
        }

        notifier = Notifier({
            val leftOutput = leftEncoderFollower.calculate(DriveSubsystem.falconDrive.leftEncoderPosition)
            val rightOutput = rightEncoderFollower.calculate(DriveSubsystem.falconDrive.rightEncoderPosition)

            var actualHeading = if (isReversed) Pathfinder.boundHalfDegrees(NavX.angle + 180.0) else NavX.angle
            actualHeading = if (isMirrored) -actualHeading else actualHeading

            val desiredHeading = Pathfinder.r2d(leftEncoderFollower.heading)

            val angleDifference = Pathfinder.boundHalfDegrees(desiredHeading - actualHeading)
            val turn = 0.8 * (-1 / 80.0) * angleDifference

            println("Left Output: ${leftOutput + turn}, Right Output: ${rightOutput - turn}")
            DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, leftOutput + turn, rightOutput - turn, squaredInputs = false)
        })
    }

    override fun initialize() {
        startTime = Timer.getFPGATimestamp()
        notifier.startPeriodic(DriveConstants.MOTION_DT)
    }

    override fun end() {

        notifier.stop()

        DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, 0.0, 0.0)

        DriveSubsystem.falconDrive.leftMotors.forEach { it.inverted = false }
        DriveSubsystem.falconDrive.rightMotors.forEach { it.inverted = true }
    }

    override fun isFinished() = (Timer.getFPGATimestamp() - startTime!!) > (mpTime - 0.1)
}