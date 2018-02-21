package frc.team5190.robot.arm

import com.ctre.phoenix.motorcontrol.*
import com.ctre.phoenix.motorcontrol.can.TalonSRX
import edu.wpi.first.wpilibj.command.Subsystem
import frc.team5190.robot.util.*

/**
 * Subsystem for controlling the arm mechanism
 */
object ArmSubsystem : Subsystem() {

    private val masterArmMotor = TalonSRX(MotorIDs.ARM)
    private val currentBuffer = CircularBuffer(25)

    private var stalled = false
    private var state = MotorState.OK

    val amperage
        get() = masterArmMotor.outputCurrent

    val currentPosition
        get() = masterArmMotor.getSelectedSensorPosition(0)

    init {
        masterArmMotor.apply {
            // Invert the motor
            this.inverted = ArmConstants.INVERTED

            // Sensors and Safety
            this.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10)
            this.setSensorPhase(ArmConstants.SENSOR_PHASE)
            this.configReverseSoftLimitEnable(true, 10)
            this.configReverseSoftLimitThreshold(ArmPosition.DOWN.ticks - 100, 10)

            // Brake Mode
            this.setNeutralMode(NeutralMode.Brake)

            // Closed Loop Control
            this.configPID(ArmConstants.PID_SLOT, ArmConstants.P, ArmConstants.I, ArmConstants.D, 10)
            this.configNominalOutput(ArmConstants.NOMINAL_OUT, -ArmConstants.NOMINAL_OUT, 10)
            this.configPeakOutput(ArmConstants.PEAK_OUT, -ArmConstants.PEAK_OUT, 10)
            this.configAllowableClosedloopError(0, ArmConstants.TOLERANCE, 10)

            // Motion Magic Control
            this.configMotionCruiseVelocity(ArmConstants.MOTION_VELOCITY, 10)
            this.configMotionAcceleration(ArmConstants.MOTION_ACCELERATION, 10)
        }

        currentBuffer.configureForTalon(ArmConstants.LOW_PEAK, ArmConstants.HIGH_PEAK, ArmConstants.DUR)
    }

    fun set(controlMode: ControlMode, output: Double) {
        masterArmMotor.set(controlMode, output)
    }

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

    override fun initDefaultCommand() {
        this.defaultCommand = ManualArmCommand()
    }

    override fun periodic() {
        this.currentLimiting()
    }
}

enum class ArmPosition(val ticks: Int) {
    BEHIND(ArmConstants.DOWN_TICKS + 1450),
    UP(ArmConstants.DOWN_TICKS + 800),
    MIDDLE(ArmConstants.DOWN_TICKS + 400),
    DOWN(ArmConstants.DOWN_TICKS);
}