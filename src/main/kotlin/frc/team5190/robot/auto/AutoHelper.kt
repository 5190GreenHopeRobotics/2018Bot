/*
 * Copyright (c) 2018 FRC Team 5190
 * Ryan Segerstrom, Prateek Machiraju
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
        /**
         * Returns an autonomous command pertaining to the FMS data
         * @param startingPositions Starting Position
         * @param switchOwnedSide The owned side of the switch
         * @param scaleOwnedSide The owned side of the scale
         */
        fun getAuto(startingPositions: StartingPositions, switchOwnedSide: MatchData.OwnedSide, scaleOwnedSide: MatchData.OwnedSide): CommandGroup {

            // Get the folder that the paths are contained within
            var folder = "${startingPositions.name.first()}S-${switchOwnedSide.name.first()}${scaleOwnedSide.name.first()}"
            if (folder[0] == 'C') folder = folder.substring(0, folder.length - 1)

            when (folder) {
                /*
                 2 Cube Autonomous -- Scale, then Switch
                 * */
                "LS-LL", "RS-RR" -> {
                    val scale1Id = Pathreader.requestPath("LS-LL", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scale1Id, folder == "RS-RR", false))
                        addSequential(pickupCube(folder == "LS-LL"))
                        addSequential(dropCubeOnSwitch())
                    }
                }

                /*
                2 Cube Autonomous -- Scale, then Scale
                 */
                "LS-RL", "RS-LR" -> {
                    val scale1Id = Pathreader.requestPath("LS-LL", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scale1Id, folder == "RS-LR", false))
                        addSequential(pickupCube(folder == "LS-RL"))
                        addSequential(switchToScale())
                    }
                }

                /*
                1 Cube Autonomous -- Switch
                 */
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

                /*
                2 Cube Autonomous -- Scale, then Switch
                 */
                "LS-RR", "RS-LL" -> {
                    val scaleId = Pathreader.requestPath("LS-RR", "Scale")
                    return commandGroup {
                        addSequential(dropCubeOnScale(scaleId, folder == "RS-LL", true))
                        addSequential(pickupCube(folder == "RS-LL"))
                        addSequential(dropCubeOnSwitch())
                    }
                }

                /*
                2 Cube Autonomous -- Switch, then Switch
                 */
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

                /*
                2 Cube Autonomous -- Switch, then Switch
                 */
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
                else -> throw IllegalArgumentException("Scenario does not exist.")
            }
        }

        /**
         * Goes from switch to scale.
         */
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

        /**
         * Picks up a cube using Vision
         * @param leftTurn Whether the turn is to the left
         */
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

        /**
         * Drops the cube on the scale
         * @param scaleId ID of the scale MP
         * @param isMirrored Whether the MP is mirrored
         * @param isOpposite whether the scale is on the opposite side of the starting position
         */
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
                            addParallel(AutoArmCommand(ArmPosition.BEHIND))
                            addParallel(commandGroup {
                                addSequential(TimedCommand(0.75))
                                addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.65, outSpeed = 0.65))
                            })
                        })
                    })
                })
                addSequential(IntakeHoldCommand(), 0.001)
            }
        }

        /**
         * Drops the cube on the switch
         */
        private fun dropCubeOnSwitch(): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH))
                    addParallel(AutoArmCommand(ArmPosition.MIDDLE))
                    addParallel(commandGroup {
                        addParallel(MotionMagicCommand(1.1), 1.0)
                        addParallel(commandGroup {
                            addSequential(TimedCommand(0.5))
                            addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, outSpeed = 0.5))
                        })
                    })
                })
                addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, outSpeed = 0.5))
                addSequential(IntakeHoldCommand(), 0.001)
            }
        }

        /**
         * Drops the cube from the center
         * @param switchId ID of the switch MP
         */
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

        /**
         * Goes from the switch to the center position
         * @param centerId ID of the center MP
         */
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

        /**
         * Picks up a cube from the center pyramid
         */
        private fun pickupCubeFromCenter(): CommandGroup {
            return commandGroup {
                addSequential(commandGroup {
                    addParallel(MotionMagicCommand(4.00))
                    addParallel(IntakeCommand(IntakeDirection.IN, timeout = 2.0))
                })
                addSequential(IntakeHoldCommand(), 0.001)
                addSequential(MotionMagicCommand(-4.25, cruiseVel = 5.0, accel = 4.0), 1.2)
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
