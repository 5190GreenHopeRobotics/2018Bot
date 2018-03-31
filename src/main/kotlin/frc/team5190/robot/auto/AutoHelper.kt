/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
 */

package frc.team5190.robot.auto

import edu.wpi.first.wpilibj.command.*
import frc.team5190.robot.arm.*
import frc.team5190.robot.drive.*
import frc.team5190.robot.elevator.*
import frc.team5190.robot.intake.*
import frc.team5190.robot.util.commandGroup
import openrio.powerup.MatchData

/**
 * Contains methods that help with autonomous
 */
class AutoHelper {
    companion object {

        fun getAuto(startingPositions: StartingPositions, switchOwnedSide: MatchData.OwnedSide, scaleOwnedSide: MatchData.OwnedSide): CommandGroup {

            // Get the folder that the paths are contained within
            var folder = "${startingPositions.name.first()}S-${switchOwnedSide.name.first()}${scaleOwnedSide.name.first()}"
            if (folder[0] == 'C') folder = folder.substring(0, folder.length - 1)

            return when (folder) {
            // Center switch autonomous cases.
                "CS-L", "CS-R" -> commandGroup {

                    val firstSwitch = MotionProfileCommand(folder, "Switch")

                    addSequential(commandGroup {
                        addParallel(firstSwitch)
                        addParallel(ElevatorPresetCommand(ElevatorPreset.SWITCH))
                        addParallel(commandGroup {
                            addSequential(TimedCommand(firstSwitch.mpTime - 0.2))
                            addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, speed = 0.5))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                    addSequential(commandGroup {
                        addParallel(MotionProfileCommand("CS-L", "Center", robotReversed = true, pathReversed =  true, pathMirrored = folder.last() == 'R'))
                        addParallel(commandGroup {
                            addSequential(TimedCommand(0.5))
                            addSequential(commandGroup {
                                addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                            })
                        })
                    })
                    addSequential(TurnCommand(0.0, false, 0.0))
                    addSequential(PickupCubeCommand(visionCheck = false), 4.0)
                    addSequential(IntakeHoldCommand(), 0.001)
                    addSequential(ArcDriveCommand(-5.0, angle = 0.0, cruiseVel = 5.0, accel = 4.0), 1.75)

                    addSequential(commandGroup {
                        addParallel(MotionProfileCommand("CS-L", "Center", pathMirrored = folder.last() == 'R'), firstSwitch.mpTime - 0.4)
                        addParallel(ElevatorPresetCommand(ElevatorPreset.SWITCH))
                        addParallel(commandGroup {
                            addSequential(TimedCommand(firstSwitch.mpTime - 0.2))
                            addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, speed = 0.5))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                }

            // Scale autonomous cases
                "LS-LL", "RS-RR", "LS-RL", "RS-LR",
                "LS-RR", "RS-LL", "LS-LR", "RS-RL" -> commandGroup {

                    val folderIn = if (folder.first() == folder.last()) "LS-LL" else "LS-RR"
                    val timeToGoUp = if (folder.first() == folder.last()) 2.50 else 1.50
                    val mpCommand = MotionProfileCommand(folderIn, "Drop First Cube", robotReversed = true, pathMirrored = folder.first() == 'R')

                    // Drop 1st Cube on Scale
                    addSequential(commandGroup {
                        addParallel(mpCommand)
                        addParallel(commandGroup {
                            addSequential(TimedCommand(0.2))
                            addSequential(object : CommandGroup() {
                                var startTime: Long = 0

                                init {
                                    addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                                    addParallel(AutoArmCommand(ArmPosition.UP))
                                }

                                override fun initialize() {
                                    super.initialize()
                                    startTime = System.currentTimeMillis()
                                }

                                override fun isFinished() = (System.currentTimeMillis() - startTime) > (mpCommand.mpTime - timeToGoUp).coerceAtLeast(0.001) * 1000
                            })
                            addSequential(commandGroup {
                                addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND))
                                addParallel(commandGroup {
                                    addSequential(object : Command() {
                                        override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                                    })
                                    addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.85, timeout = 0.5))
                                    addSequential(IntakeHoldCommand(), 0.001)
                                })
                            })
                        })
                    })


                    // Pickup 2nd Cube
                    addSequential(commandGroup {
                        addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                        addParallel(IntakeCommand(IntakeDirection.IN, speed = 1.0, timeout = 5.0))
                        addParallel(object : MotionProfileCommand(folderIn, "Pickup Second Cube", pathMirrored = folder.last() == 'R') {
                            override fun isFinished() = super.isFinished() && IntakeSubsystem.isCubeIn
                        })
                    })

                    addSequential(IntakeHoldCommand(), 0.001)

                    // Drop 2nd Cube in Scale
                    addSequential(commandGroup {
                        addParallel(MotionProfileCommand(folderIn, "Pickup Second Cube", robotReversed = true, pathReversed = true, pathMirrored = folder.last() == 'R'))
                        addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND))
                        addParallel(commandGroup {
                            addSequential(object : Command() {
                                override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                            })
                            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.65, timeout = 0.5))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })

                    // Pickup 3rd Cube
                    addSequential(commandGroup {
                        addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                        addParallel(IntakeCommand(IntakeDirection.IN, speed = 1.0, timeout = 5.0))
                        addParallel(object : MotionProfileCommand(folderIn, "Pickup Third Cube", pathMirrored = folder.last() == 'R') {
                            override fun isFinished() = super.isFinished() || IntakeSubsystem.isCubeIn
                        })
                    })

                    // Drop 3rd Cube in Scale
                    addSequential(commandGroup {
                        addParallel(MotionProfileCommand(folderIn, "Pickup Third Cube", robotReversed = true, pathReversed = true, pathMirrored = folder.last() == 'R'))
                        addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND))
                        addParallel(commandGroup {
                            addSequential(object : Command() {
                                override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                            })
                            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.55, timeout = 0.5))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                }

                else -> throw IllegalArgumentException("Scenario does not exist.")
            }
        }
    }
}

/**
 * Stores starting position of the robot.
 */
enum class StartingPositions {
    LEFT, CENTER, RIGHT;
}
