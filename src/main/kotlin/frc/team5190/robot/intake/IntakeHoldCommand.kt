package frc.team5190.robot.intake

import com.ctre.phoenix.motorcontrol.ControlMode
import edu.wpi.first.wpilibj.command.Command

/**
 *  Default command that holds the cube while the robot is moving
 */
class IntakeHoldCommand : Command() {

    init {
        requires(IntakeSubsystem)
    }

    override fun initialize() {
        IntakeSubsystem.intakeSolenoid.set(true)
        IntakeSubsystem.intakeTalon.set(ControlMode.PercentOutput, 0.0)
    }

    override fun isFinished() = false
}