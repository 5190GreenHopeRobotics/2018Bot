/**
 * FRC Team 5190
 * Programming Team
 */

package frc.team5190.robot.auto

import edu.wpi.first.wpilibj.command.CommandGroup
import edu.wpi.first.wpilibj.command.TimedCommand
import frc.team5190.robot.arm.ArmPosition
import frc.team5190.robot.arm.AutoArmCommand
import frc.team5190.robot.elevator.AutoElevatorCommand
import frc.team5190.robot.elevator.ElevatorPosition
import frc.team5190.robot.intake.*
import frc.team5190.robot.pathreader.Pathreader
import frc.team5190.robot.util.commandGroup
import openrio.powerup.MatchData

/**
 * Contains methods that help with autonomous
 */
class AutoHelper {
    companion object {
        fun getAuto(startingPositions: StartingPositions, switchOwnedSide: MatchData.OwnedSide, scaleOwnedSide: MatchData.OwnedSide): CommandGroup {

            var folder = "${startingPositions.name.first()}S-${switchOwnedSide.name.first()}${scaleOwnedSide.name.first()}"
            if (folder[0] == 'C') folder = folder.substring(0, folder.length - 1)

            when (folder) {
                "LS-LL", "RS-RR" -> {
                    val scale1Id = Pathreader.requestPath("LS-LL", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scale1Id, folder == "RS-RR", false))
                        addSequential(pickupCube(folder == "LS-LL"))
                        addSequential(dropCubeOnSwitch())
                    }
                }
                "LS-RL", "RS-LR" -> {
                    val scale1Id = Pathreader.requestPath("LS-LL", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scale1Id, folder == "RS-LR", false))
                        addSequential(pickupCube(folder == "LS-RL"))
                        addSequential(switchToScale())
                    }
                }
                "LS-LR", "RS-RL" -> {
                    val switchId = Pathreader.requestPath("LS-LR", "Switch")
                    return commandGroup {
                        addSequential(commandGroup {
                            addParallel(MotionProfileCommand(switchId, true, folder == "RS-RL"))
                            addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                            addParallel(AutoArmCommand(ArmPosition.UP))
                        })
                        addSequential(TurnCommand(-90.0, false))
                        addSequential(frc.team5190.robot.util.commandGroup {
                            addParallel(AutoArmCommand(frc.team5190.robot.arm.ArmPosition.DOWN))
                            addParallel(MotionMagicCommand(2.5), 1.0)
                        })
                        addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, outSpeed = 0.4))
                        addSequential(IntakeHoldCommand(), 0.001)
                    }
                }
                "LS-RR", "RS-LL" -> {
                    val scaleId = Pathreader.requestPath("LS-RR", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scaleId, folder == "RS-LL", true))
                        addSequential(pickupCube(folder == "RS-LL"))
                        addSequential(dropCubeOnSwitch())
                    }
                }
                "CS-L" -> {
                    val switchId = Pathreader.requestPath("CS-L", "Switch")
                    val centerId = Pathreader.requestPath("CS-L", "Center")
                    val switch2Id = Pathreader.requestPath("CS-L", "Switch 2")
                    return commandGroup {
                        addSequential(dropCubeFromCenter(switchId))
                        addSequential(getBackToCenter(centerId))
                        addSequential(pickupCubeFromCenter())
                        addSequential(dropCubeFromCenter(switch2Id))
                        addSequential((MotionMagicCommand(-2.00)))
                    }
                }
                "CS-R" -> {
                    val switchId = Pathreader.requestPath("CS-R", "Switch")
                    val centerId = Pathreader.requestPath("CS-R", "Center")
                    val switch2Id = Pathreader.requestPath("CS-R", "Switch 2")
                    return commandGroup {
                        addSequential(dropCubeFromCenter(switchId))
                        addSequential(getBackToCenter(centerId))
                        addSequential(pickupCubeFromCenter())
                        addSequential(dropCubeFromCenter(switch2Id))
                        addSequential((MotionMagicCommand(-2.00)))
                    }
                }
                else -> TODO("Does not exist.")
            }
        }

        private fun switchToScale(): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(AutoElevatorCommand(ElevatorPosition.SCALE))
                    addParallel(AutoArmCommand(ArmPosition.BEHIND))
                    addParallel(commandGroup {
                        addSequential(MotionMagicCommand(-4.5))
                        addSequential(TurnCommand(12.5))
                    })
                })

                addSequential(IntakeCommand(IntakeDirection.OUT, outSpeed = 1.0, timeout = 1.0))
                addSequential(AutoArmCommand(frc.team5190.robot.arm.ArmPosition.MIDDLE))
            }

        }

        private fun pickupCube(leftTurn: Boolean): CommandGroup {
            return commandGroup {
                addParallel(commandGroup {
                    addParallel(AutoElevatorCommand(ElevatorPosition.INTAKE))
                    addParallel(AutoArmCommand(ArmPosition.DOWN))
                })
                addParallel(commandGroup {
                    addSequential(TurnCommand(if (leftTurn) -10.0 else 5.0, visionCheck = true, tolerance = 10.0))
                    addSequential(commandGroup {
                        addParallel(MotionMagicCommand(5.0, cruiseVel = 5.0))
                        addParallel(IntakeCommand(IntakeDirection.IN, timeout = 2.25, inSpeed = 0.75))
                    })
                    addSequential(IntakeHoldCommand(), 0.001)
                })
            }
        }

        private fun dropCubeOnScale(scaleId: Int, isMirrored: Boolean, isOpposite: Boolean): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(MotionProfileCommand(scaleId, true, isMirrored))
                    addParallel(commandGroup {
                        addSequential(commandGroup {
                            addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                            addParallel(AutoArmCommand(ArmPosition.UP))
                        }, 0.1)
                        addSequential(TimedCommand(if (isOpposite) 5.0 else 2.25))
                        addSequential(commandGroup {
                            addParallel(AutoElevatorCommand(ElevatorPosition.SCALE))
                            addParallel(commandGroup {
                                addSequential(TimedCommand(0.25))
                                addSequential(AutoArmCommand(ArmPosition.BEHIND))
                            })
                        })
                        addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.65, outSpeed = 0.65))
                    })
                })
                addSequential(IntakeHoldCommand(), 0.001)
            }
        }

        private fun dropCubeOnSwitch(): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                    addParallel(AutoArmCommand(ArmPosition.MIDDLE))
                    addParallel(commandGroup {
                        addSequential(TimedCommand(0.75))
                        addSequential(MotionMagicCommand(1.1), 1.0)
                    })
                })
                addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, outSpeed = 0.5))
                addSequential(IntakeHoldCommand(), 0.001)
            }
        }

        private fun dropCubeFromCenter(switchId: Int): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(MotionProfileCommand(switchId))
                    addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                    addParallel(AutoArmCommand(ArmPosition.MIDDLE))
                })

                addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, outSpeed = 0.5))
                addSequential(IntakeHoldCommand(), 0.001)
            }
        }

        private fun getBackToCenter(centerId: Int): CommandGroup {
            return commandGroup {
                addSequential(MotionProfileCommand(centerId, true))
                addSequential(commandGroup {
                    addParallel(TurnCommand(0.0, false, 0.0))
                    addParallel(AutoElevatorCommand(ElevatorPosition.INTAKE))
                    addParallel(AutoArmCommand(ArmPosition.DOWN))
                })
            }
        }

        private fun pickupCubeFromCenter(): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(MotionMagicCommand(4.00))
                    addParallel(IntakeCommand(IntakeDirection.IN, timeout = 2.0))
                })
                addSequential(IntakeHoldCommand(), 0.001)
                addSequential(MotionMagicCommand(-4.50))
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
