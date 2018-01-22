package frc.team5190.robot.elevator

import edu.wpi.first.wpilibj.GenericHID
import edu.wpi.first.wpilibj.command.Command
import edu.wpi.first.wpilibj.command.TimedCommand
import frc.team5190.robot.MainXbox

class ManualElevatorCommand : Command() {

    init {
        requires(ElevatorSubsystem)
    }

    override fun execute() {
        when {
            MainXbox.getBumper(GenericHID.Hand.kLeft) -> ElevatorSubsystem.set(0.2)
            MainXbox.getBumper(GenericHID.Hand.kRight) -> ElevatorSubsystem.set(-0.2)
            else -> ElevatorSubsystem.set(0.0)
        }
    }

    override fun end() {
        ElevatorSubsystem.set(0.0)
    }

    override fun isFinished() = false
}