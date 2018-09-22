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
import jaci.pathfinder.Pathfinder
import jaci.pathfinder.Trajectory
import jaci.pathfinder.followers.EncoderFollower

open class MotionProfileCommand(folder: String, file: String,
                                private val robotReversed: Boolean = false, private val pathReversed: Boolean = false,
                                private val pathMirrored: Boolean = false, private val useGyro: Boolean = true) : Command() {

    companion object {
        var robotPosition: Pair<Double, Double>? = null
    }

    // Notifier and Sync
    private val syncNotifier = Object()
    private var stopNotifier = false
    private val notifier: Notifier

    // Paths to follow
    private val leftPath: Trajectory
    private val rightPath: Trajectory

    // Follower objects
    private val leftEncoderFollower: EncoderFollower
    private val rightEncoderFollower: EncoderFollower

    // Robot pose according to spline
    private val currentRobotPosition: Pair<Double, Double>?
        get() {
            if(leftEncoderFollower.isFinished || rightEncoderFollower.isFinished) return null
            val x1 = leftEncoderFollower.segment.x
            val y1 = leftEncoderFollower.segment.y
            val x2 = rightEncoderFollower.segment.x
            val y2 = rightEncoderFollower.segment.y
            return (x1 + x2) / 2.0 to ((y1 + y2) / 2.0).let {
                if(pathMirrored) 27.0 - it
                else it
            }
        }

    // Time path takes to execute
    val pathDuration
        get() = leftPath.length() * DriveConstants.MOTION_DT

    private var startTime: Double? = null

    init {
        // FastTrajectories for each side of the drivetrain
        val trajectories = Pathreader.getPath(folder, file)
        leftPath = trajectories[0].let { if (pathReversed) reverseTrajectory(it) else it }
        rightPath = trajectories[1].let { if (pathReversed) reverseTrajectory(it) else it }

        this.requires(DriveSubsystem)

        // Mirror trajectories if needed
        val leftTrajectory = if (pathMirrored) rightPath else leftPath
        val rightTrajectory = if (pathMirrored) leftPath else rightPath

        // Negative multiplier for backward travel
        val robotReversedMul = if (robotReversed) -1 else 1

        // Setup follower constants
        leftEncoderFollower = EncoderFollower(if (robotReversed xor pathReversed) rightTrajectory else leftTrajectory).apply {
            configureEncoder(0, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(DriveConstants.P_HIGH, DriveConstants.I_HIGH, DriveConstants.D_HIGH, 1 / 15.0, 0.0)
        }

        rightEncoderFollower = EncoderFollower(if (robotReversed xor pathReversed) leftTrajectory else rightTrajectory).apply {
            configureEncoder(0, DriveConstants.SENSOR_UNITS_PER_ROTATION, DriveConstants.WHEEL_RADIUS / 6.0)
            configurePIDVA(DriveConstants.P_HIGH, DriveConstants.I_HIGH, DriveConstants.D_HIGH, 1 / 15.0, 0.0)
        }

        // Notifier to run in a constant loop
        notifier = Notifier {
            synchronized(syncNotifier) {
                if (stopNotifier) {
                    println("Oof MotionProfile Notifier still running!")
                    return@Notifier
                }

                // Raw output from follower
                val leftOutput = leftEncoderFollower.calculate(DriveSubsystem.falconDrive.leftEncoderPosition * robotReversedMul).coerceIn(-0.1 , 1.0) * robotReversedMul
                val rightOutput = rightEncoderFollower.calculate(DriveSubsystem.falconDrive.rightEncoderPosition * robotReversedMul).coerceIn(-0.1, 1.0) * robotReversedMul


                // Heading correction
                val actualHeading = Pathfinder.boundHalfDegrees((Pigeon.correctedAngle + if (robotReversed xor pathReversed) 180 else 0))
                val desiredHeading = (if (pathMirrored) -1 else 1) * Pathfinder.boundHalfDegrees(Pathfinder.r2d(leftEncoderFollower.heading))

                val angleDifference = Pathfinder.boundHalfDegrees(actualHeading - desiredHeading)

                var turn = 1.7 * (1 / 80.0) * angleDifference
                turn = if (useGyro) turn else 0.0

                println(angleDifference)

                // Output total calculate value to motors and update robot pose
                DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, leftOutput + turn, rightOutput - turn, squaredInputs = false)
                robotPosition = currentRobotPosition
            }
        }

    }


    // Function that reverses the trajectory
    private fun reverseTrajectory(trajectory: Trajectory): Trajectory {
        val newTrajectory = trajectory.copy()
        val distance = newTrajectory.segments.last().position
        newTrajectory.segments.reverse()
        newTrajectory.segments.forEach {
            it.position = distance - it.position
        }
        return newTrajectory
    }

    // Initializes the command
    override fun initialize() {
        DriveSubsystem.resetEncoders()

        startTime = Timer.getFPGATimestamp()
        stopNotifier = false
        notifier.startPeriodic(DriveConstants.MOTION_DT)
    }

    // Called when command ends
    override fun end() {
        synchronized(syncNotifier) {
            stopNotifier = true
            notifier.stop()
            robotPosition = null
            DriveSubsystem.falconDrive.tankDrive(ControlMode.PercentOutput, 0.0, 0.0)
        }
    }

    // Checks command for completion
    override fun isFinished() = leftEncoderFollower.isFinished && rightEncoderFollower.isFinished
}
