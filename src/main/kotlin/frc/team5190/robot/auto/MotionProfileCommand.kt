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
import frc.team5190.robot.sensors.Pigeon
import frc.team5190.robot.util.DriveConstants
import frc.team5190.robot.util.Maths
import jaci.pathfinder.Pathfinder
import jaci.pathfinder.Trajectory
import jaci.pathfinder.followers.EncoderFollower

open class MotionProfileCommand(folder: String, file: String,
                                private val robotReversed: Boolean = false, private val pathReversed: Boolean = false,
                                pathMirrored: Boolean = false, useGyro: Boolean = true) : Command() {

    private val syncNotifier = Object()
    private var stopNotifier = false

    private val leftPath: Trajectory
    private val rightPath: Trajectory

    private val leftEncoderFollower: EncoderFollower
    private val rightEncoderFollower: EncoderFollower

    val robotPosition: Pair<Double, Double>?
        get() {
            if(leftEncoderFollower.isFinished || rightEncoderFollower.isFinished) return null
            val x1 = leftEncoderFollower.segment.x
            val y1 = leftEncoderFollower.segment.y
            val x2 = rightEncoderFollower.segment.x
            val y2 = rightEncoderFollower.segment.y
            return (x1 + x2) / 2.0 to (y1 + y2) / 2.0
        }

    val pathDuration
        get() = leftPath.length() * DriveConstants.MOTION_DT

    private val notifier: Notifier

    private var startTime: Double? = null

    init {
        val trajectories = Pathreader.getPath(folder, file)
        leftPath = trajectories[0].let { if (pathReversed) reverseTrajectory(it) else it }
        rightPath = trajectories[1].let { if (pathReversed) reverseTrajectory(it) else it }

        this.requires(DriveSubsystem)

        val leftTrajectory = if (pathMirrored) rightPath else leftPath
        val rightTrajectory = if (pathMirrored) leftPath else rightPath

        val robotReversedMul = if (robotReversed) -1 else 1

        leftEncoderFollower = EncoderFollower(if (robotReversed xor pathReversed) rightTrajectory else leftTrajectory).apply {
            configureEncoder(DriveSubsystem.falconDrive.leftEncoderPosition * robotReversedMul, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(DriveConstants.P_HIGH, DriveConstants.I_HIGH, DriveConstants.D_HIGH, 1 / Maths.rpmToFeetPerSecond(DriveConstants.MAX_RPM_HIGH, DriveConstants.WHEEL_RADIUS), 0.0)
        }

        rightEncoderFollower = EncoderFollower(if (robotReversed xor pathReversed) leftTrajectory else rightTrajectory).apply {
            configureEncoder(DriveSubsystem.falconDrive.rightEncoderPosition * robotReversedMul, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(DriveConstants.P_HIGH, DriveConstants.I_HIGH, DriveConstants.D_HIGH, 1 / Maths.rpmToFeetPerSecond(DriveConstants.MAX_RPM_HIGH, DriveConstants.WHEEL_RADIUS), 0.0)
        }

        notifier = Notifier {
            synchronized(syncNotifier) {
                if (stopNotifier) {
                    println("Oof MotionProfile Notifier still running!")
                    return@Notifier
                }

                val leftOutput = leftEncoderFollower.calculate(DriveSubsystem.falconDrive.leftEncoderPosition * robotReversedMul).coerceAtLeast(-0.1) * robotReversedMul
                val rightOutput = rightEncoderFollower.calculate(DriveSubsystem.falconDrive.rightEncoderPosition * robotReversedMul).coerceAtLeast(-0.1) * robotReversedMul

                val actualHeading = Pathfinder.boundHalfDegrees((Pigeon.correctedAngle + if (robotReversed xor pathReversed) 180 else 0))
                val desiredHeading = (if (pathMirrored) -1 else 1) * Pathfinder.boundHalfDegrees(Pathfinder.r2d(leftEncoderFollower.heading))

                val angleDifference = Pathfinder.boundHalfDegrees(actualHeading - desiredHeading)

                var turn = 1.0 * (1 / 80.0) * angleDifference
                turn = if (useGyro) turn else 0.0

                //println("Angle Difference: $angleDifference, Left: ${leftEncoderFollower.segment.velocity} Left Actual: ${Maths.nativeUnitsPer100MsToFeetPerSecond(DriveSubsystem.falconDrive.leftMaster.getSelectedSensorVelocity(0).absoluteValue)}")

                DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, leftOutput + turn, rightOutput - turn, squaredInputs = false)
            }
        }
    }

    private fun reverseTrajectory(trajectory: Trajectory): Trajectory {
        val newTrajectory = trajectory.copy()
        val distance = newTrajectory.segments.last().position
        newTrajectory.segments.reverse()
        newTrajectory.segments.forEach {
            it.position = distance - it.position
        }
        return newTrajectory
    }

    override fun initialize() {
        DriveSubsystem.resetEncoders()

        startTime = Timer.getFPGATimestamp()
        stopNotifier = false
        notifier.startPeriodic(DriveConstants.MOTION_DT)
    }

    override fun end() {
        synchronized(syncNotifier) {
            stopNotifier = true
            notifier.stop()
            DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, 0.0, 0.0)
        }
    }

    override fun isFinished() = leftEncoderFollower.isFinished && rightEncoderFollower.isFinished
}
