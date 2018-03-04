/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.arm

import com.ctre.phoenix.motorcontrol.*
import com.ctre.phoenix.motorcontrol.can.TalonSRX
import edu.wpi.first.wpilibj.command.Subsystem
import frc.team5190.robot.util.*

/**
 * Subsystem for controlling the arm mechanism
 */
object ArmSubsystem : Subsystem() {

    // Master arm talon
    private val masterArmMotor = TalonSRX(MotorIDs.ARM)

    // Buffer to hold amperage values for current limiting
    private val currentBuffer = CircularBuffer(25)

    // Variables for current limiting
    private var stalled = false
    private var state = MotorState.OK

    // Returns the amperage of the motor
    val amperage
        get() = masterArmMotor.outputCurrent

    // Returns the current encoder position of the motor
    val currentPosition
        get() = masterArmMotor.getSelectedSensorPosition(0)

    init {
        with(masterArmMotor) {
            // Motor Inversion
            inverted = ArmConstants.INVERTED

            // Sensors and Safety
            configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10)
            setSensorPhase(ArmConstants.SENSOR_PHASE)
            configReverseSoftLimitEnable(true, 10)
            configReverseSoftLimitThreshold(ArmPosition.DOWN.ticks - 100, 10)

            // Brake Mode
            setNeutralMode(NeutralMode.Brake)

            // Closed Loop Control
            configPID(ArmConstants.PID_SLOT, ArmConstants.P, ArmConstants.I, ArmConstants.D, 10)
            configNominalOutput(ArmConstants.NOMINAL_OUT, -ArmConstants.NOMINAL_OUT, 10)
            configPeakOutput(ArmConstants.PEAK_OUT, -ArmConstants.PEAK_OUT, 10)
            configAllowableClosedloopError(0, ArmConstants.TOLERANCE, 10)

            // Motion Magic Control
            configMotionCruiseVelocity(ArmConstants.MOTION_VELOCITY, 10)
            configMotionAcceleration(ArmConstants.MOTION_ACCELERATION, 10)
            setStatusFramePeriod(StatusFrameEnhanced.Status_13_Base_PIDF0, 10, 10)
            setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, 10, 10)

            configClosedloopRamp(0.3, 10)
            configOpenloopRamp(0.5, 10)
        }

        // Configure current limiting
        currentBuffer.configureForTalon(ArmConstants.LOW_PEAK, ArmConstants.HIGH_PEAK, ArmConstants.DUR)
    }

    /**
     * Sets the motor output.
     * @param controlMode Control Mode for the Talon
     * @param output Output to the motor
     */
    fun set(controlMode: ControlMode, output: Double) {
        masterArmMotor.set(controlMode, output)
    }

    /**
     * Enables current limiting on the motor so we don't stall it
     */
    private fun currentLimiting() {
        currentBuffer.add(masterArmMotor.outputCurrent)
        state = limitCurrent(currentBuffer)

        when (state) {
            MotorState.OK -> {
                if (stalled) {
                    masterArmMotor.configPeakOutput(ArmConstants.PEAK_OUT * ArmConstants.LIMITING_REDUCTION_FACTOR, -ArmConstants.PEAK_OUT * ArmConstants.LIMITING_REDUCTION_FACTOR, 10)
                } else {
                    masterArmMotor.configPeakOutput(ArmConstants.PEAK_OUT, -ArmConstants.PEAK_OUT, 10)
                }
            }
            MotorState.STALL -> {
                masterArmMotor.configPeakOutput(ArmConstants.PEAK_OUT * ArmConstants.LIMITING_REDUCTION_FACTOR, -ArmConstants.PEAK_OUT * ArmConstants.LIMITING_REDUCTION_FACTOR, 10)
                stalled = true
            }
            MotorState.GOOD -> {
                masterArmMotor.configPeakOutput(ArmConstants.PEAK_OUT, -ArmConstants.PEAK_OUT, 10)
                stalled = false
            }
        }
    }

    /**
     * Sets the default command for the subsytem
     */
    override fun initDefaultCommand() {
        defaultCommand = ManualArmCommand()
    }

    /**
     * Runs periodically
     */
    override fun periodic() {
        currentLimiting()
    }
}

/**
 * Enum class that holds the different arm positions.
 */
enum class ArmPosition(val ticks: Int) {
    BEHIND(ArmConstants.DOWN_TICKS + 1450),
    ALL_UP(ArmConstants.DOWN_TICKS + 1100),
    UP(ArmConstants.DOWN_TICKS + 800),
    MIDDLE(ArmConstants.DOWN_TICKS + 350),
    DOWN(ArmConstants.DOWN_TICKS);
}