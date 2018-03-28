/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.diagnostics

import edu.wpi.first.wpilibj.command.CommandGroup
import frc.team5190.robot.util.commandGroup

object Diagnostics : CommandGroup() {

    private val armSubsystemDiagnostics = ArmSubsystemDiagnostics()
    private val elevatorSubsystemDiagnostics = ElevatorSubsystemDiagnostics()
    private val driveSubsystemDiagnostics = DriveSubsystemDiagnostics()

    init {
        addSequential(commandGroup {
            addParallel(driveSubsystemDiagnostics)
            addParallel(IntakeSubsystemDiagnostics())
        })
        addSequential(armSubsystemDiagnostics)
        addSequential(elevatorSubsystemDiagnostics)
        addSequential(ClosedLoopDiagnostics())
    }

    override fun execute() {
        if ((armSubsystemDiagnostics.isCompleted && !armSubsystemDiagnostics.hasPassedTest) ||
                (driveSubsystemDiagnostics.isCompleted && !driveSubsystemDiagnostics.hasPassedTest) ||
                (elevatorSubsystemDiagnostics.isCompleted && !elevatorSubsystemDiagnostics.hasPassedTest)) {
            end()
        }
    }

}