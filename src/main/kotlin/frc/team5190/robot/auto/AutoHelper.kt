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
        fun getAuto(startingPositions: StartingPositions, switchOwnedSide: MatchData.OwnedSide, scaleOwnedSide: MatchData.OwnedSide, sameSideAutoMode: AutoModes, crossAutoMode: AutoModes): CommandGroup {

            var folder = "${startingPositions.name.first()}S-${switchOwnedSide.name.first()}${scaleOwnedSide.name.first()}"
            if (folder[0] == 'C') folder = folder.substring(0, folder.length - 1)

            val isRightStart = folder.first() == 'R'
            val folderIn = if (folder.first() == folder.last()) "LS-LL" else "LS-RR"

            return when (folder) {
                "CS-L", "CS-R" -> commandGroup {
                    val firstSwitch = MotionProfileCommand(folder, "Switch", false, false)

                    addSequential(commandGroup {
                        addParallel(firstSwitch)
                        addParallel(ElevatorPresetCommand(ElevatorPreset.SWITCH), 3.0)
                        addParallel(commandGroup {
                            addSequential(TimedCommand(firstSwitch.pathDuration - 0.2))
                            addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, speed = 0.5))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                    addSequential(commandGroup {
                        addParallel(MotionProfileCommand(folder, "Center", robotReversed = true, pathReversed = true))
                        addParallel(commandGroup {
                            addSequential(TimedCommand(0.5))
                            addSequential(commandGroup {
                                addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                            })
                        })
                    })
                    addSequential(TurnCommand(angle = if (folder.last() == 'L') -2.0 else 0.0), 1.0)
                    addSequential(PickupCubeCommand(visionCheck = false), 4.0)
                    addSequential(IntakeHoldCommand(), 0.001)
                    addSequential(ArcDriveCommand(-5.0, angle = 0.0, cruiseVel = 5.0, accel = 4.0), 1.75)

//                    addSequential(commandGroup {
//                        addParallel(MotionProfileCommand(folder, "Switch", false, false), firstSwitch.pathDuration - 0.4)
//                        addParallel(ElevatorPresetCommand(ElevatorPreset.SWITCH))
////                        addParallel(commandGroup {
////                            addSequential(TimedCommand(firstSwitch.pathDuration - 0.2))
////                            addSequential(IntakeCommand(IntakeDirection.OUT, timeout = 0.2, speed = 0.5))
////                            addSequential(IntakeHoldCommand(), 0.001)
////                        })
//                    })
                }


                "LS-LL", "LS-RL", "RS-RR", "RS-LR" -> when (sameSideAutoMode) {
                    AutoModes.FULL -> getFullAuto(folderIn, isRightStart, scaleOwnedSide)
                    AutoModes.SIMPLE -> getSimpleAuto(folderIn, isRightStart)
                    AutoModes.CARRY_ALLIANCE -> getCarryEntireAllianceAuto(switchOwnedSide, scaleOwnedSide, folder, sameSideAuto = true)
                    AutoModes.SWITCH -> if (switchOwnedSide.name.first().toUpperCase() == folder.first()) getSwitchAuto(isRightStart) else getBaselineAuto()
                    AutoModes.BASELINE -> getBaselineAuto()
                }

                "LS-RR", "LS-LR", "RS-LL", "RS-RL" -> when (crossAutoMode) {
                    AutoModes.FULL -> getFullAuto(folderIn, isRightStart, scaleOwnedSide)
                    AutoModes.SIMPLE -> getSimpleAuto(folderIn, isRightStart)
                    AutoModes.CARRY_ALLIANCE -> getCarryEntireAllianceAuto(switchOwnedSide, scaleOwnedSide, folder, sameSideAuto = false)
                    AutoModes.SWITCH -> if (switchOwnedSide.name.first().toUpperCase() == folder.first()) getSwitchAuto(isRightStart) else getBaselineAuto()
                    AutoModes.BASELINE -> getBaselineAuto()
                }

                else -> {
                    commandGroup { }
                }
            }
        }


        private fun getBaselineAuto() = commandGroup {
            addSequential(StraightDriveCommand(distance = -12.0))
        }

        private fun getSwitchAuto(isRightStart: Boolean) = commandGroup {
            addSequential(commandGroup {
                addParallel(MotionProfileCommand("LS-LL", "Switch", robotReversed = true, pathMirrored = isRightStart))
                addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH), 1.5)
                addParallel(AutoArmCommand(ArmPosition.UP), 1.5)
            })

            addSequential(TurnCommand(90.0 * if (isRightStart) 1 else -1), 1.5)
            addSequential(commandGroup {
                addParallel(AutoArmCommand(ArmPosition.DOWN), 1.5)
                addParallel(StraightDriveCommand(2.5), 1.0)
            })

            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.4, timeout = 1.0))
            addSequential(IntakeHoldCommand(), 0.001)
            addSequential(StraightDriveCommand(-2.0))
            addSequential(ElevatorPresetCommand(ElevatorPreset.INTAKE))
        }

        private fun getCarryEntireAllianceAuto(switchOwnedSide: MatchData.OwnedSide, scaleOwnedSide: MatchData.OwnedSide, folder: String, sameSideAuto: Boolean): CommandGroup {

            // Gives us a 75% chance of getting the switch when we start from the side

            val isRightStart = folder.first() == 'R'
            val folderIn = if (folder.first() == folder.last()) "LS-LL" else "LS-RR"

            // Scale on our side and switch on other side
            return if (sameSideAuto && folder.first() != switchOwnedSide.name.first().toUpperCase()) getFullAuto(folderIn, isRightStart, scaleOwnedSide)

            // Scale on other side and switch on our side
            else if (!sameSideAuto && folder.first() == switchOwnedSide.name.first().toUpperCase()) getSwitchAuto(isRightStart)

            // Scale and switch on same side
            else commandGroup {
                val timeToGoUp = if (folderIn.first() == folderIn.last()) 2.50 else 1.50
                val firstCube = object : MotionProfileCommand(folderIn, "Drop First Cube", robotReversed = true, pathMirrored = isRightStart) {
                    override fun isFinished(): Boolean {
                        return super.isFinished() || (ElevatorSubsystem.currentPosition > ElevatorPosition.FIRST_STAGE.ticks && !IntakeSubsystem.isCubeIn && ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100)
                    }
                }

                // Drop First Cube in Scale
                addSequential(commandGroup {
                    addParallel(firstCube)
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

                            override fun isFinished() = (System.currentTimeMillis() - startTime) > (firstCube.pathDuration - timeToGoUp).coerceAtLeast(0.001) * 1000
                        })
                        addSequential(commandGroup {
                            addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND_LIDAR))
                            addParallel(commandGroup {
                                addSequential(object : Command() {
                                    override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                                })
                                addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.50, timeout = 0.50))
                                addSequential(IntakeHoldCommand(), 0.001)
                            })
                        })
                    })
                })

                // Pickup Second Cube
                addSequential(commandGroup {
                    addSequential(commandGroup {
                        addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                        addParallel(IntakeCommand(IntakeDirection.IN, speed = 1.0, timeout = 10.0))
                        addParallel(object : MotionProfileCommand("LS-LL", "Pickup Second Cube", pathMirrored = scaleOwnedSide == MatchData.OwnedSide.RIGHT) {

                            var startTime: Long = 0L

                            override fun initialize() {
                                super.initialize()
                                startTime = System.currentTimeMillis()
                            }

                            override fun isFinished() = super.isFinished() || ((System.currentTimeMillis() - startTime > 750) && IntakeSubsystem.isCubeIn)
                        })
                    })
                    addSequential(IntakeHoldCommand(), 0.001)
                })

                // Drop Second Cube in Switch
                addSequential(commandGroup {
                    addSequential(StraightDriveCommand(-0.5), 0.75)
                    addSequential(ElevatorPresetCommand(ElevatorPreset.SWITCH), 1.5)
                    addSequential(StraightDriveCommand(1.0), 0.75)
                    addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.5, timeout = 0.5))
                    addSequential(IntakeHoldCommand(), 0.001)
                })
            }
        }

        private fun getSimpleAuto(folder: String, isRightStart: Boolean) = commandGroup {
            addSequential(commandGroup {
                addParallel(MotionProfileCommand(folder, "Simple", robotReversed = true, pathMirrored = isRightStart))
                addParallel(AutoElevatorCommand(ElevatorPosition.SWITCH), 1.5)
                addParallel(AutoArmCommand(ArmPosition.UP), 1.5)
            })
            addSequential(commandGroup {
                addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND_LIDAR))
                addParallel(commandGroup {
                    addSequential(object : Command() {
                        override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                    })
                    addSequential(IntakeCommand(IntakeDirection.OUT, speed = 1.0, timeout = 1.0))
                    addSequential(IntakeHoldCommand(), 0.001)
                })
            })
        }

        private fun getFullAuto(folderIn: String, isRightStart: Boolean, scaleOwnedSide: MatchData.OwnedSide) = commandGroup {

            val timeToGoUp = if (folderIn.first() == folderIn.last()) 2.50 else 1.50
            val firstCube = object : MotionProfileCommand(folderIn, "Drop First Cube", robotReversed = true, pathMirrored = isRightStart) {
                override fun isFinished(): Boolean {
                    return super.isFinished() || (ElevatorSubsystem.currentPosition > ElevatorPosition.FIRST_STAGE.ticks && !IntakeSubsystem.isCubeIn && ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100)
                }
            }

            /*
            Drop 1st Cube in Scale
            */
            addSequential(commandGroup {
                addParallel(firstCube)
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

                        override fun isFinished() = (System.currentTimeMillis() - startTime) > (firstCube.pathDuration - timeToGoUp).coerceAtLeast(0.001) * 1000
                    })
                    addSequential(commandGroup {
                        addParallel(ElevatorPresetCommand(ElevatorPreset.BEHIND_LIDAR))
                        addParallel(commandGroup {
                            addSequential(object : Command() {
                                override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                            })
                            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.50, timeout = 0.50))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                })
            })

            /*
            Pickup 2nd Cube
             */
            addSequential(commandGroup {
                addSequential(commandGroup {
                    addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                    addParallel(IntakeCommand(IntakeDirection.IN, speed = 1.0, timeout = 10.0))
                    addParallel(object : MotionProfileCommand("LS-LL", "Pickup Second Cube", pathMirrored = scaleOwnedSide == MatchData.OwnedSide.RIGHT) {

                        var startTime: Long = 0L

                        override fun initialize() {
                            super.initialize()
                            startTime = System.currentTimeMillis()
                        }

                        override fun isFinished() = super.isFinished() || ((System.currentTimeMillis() - startTime > 750) && IntakeSubsystem.isCubeIn)
                    })
                })
                addSequential(IntakeHoldCommand(), 0.001)

            })

            /*
             Drop 2nd Cube in Scale
              */
            addSequential(commandGroup {
                val dropSecondCubePath = object : MotionProfileCommand("LS-LL", "Pickup Second Cube", robotReversed = true, pathReversed = true, pathMirrored = scaleOwnedSide == MatchData.OwnedSide.RIGHT) {
                    override fun isFinished(): Boolean {
                        return super.isFinished() || (ElevatorSubsystem.currentPosition > ElevatorPosition.FIRST_STAGE.ticks && !IntakeSubsystem.isCubeIn && ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100)
                    }
                }
                addParallel(dropSecondCubePath)
                addParallel(commandGroup {
                    addSequential(commandGroup {
                        addParallel(commandGroup {
                            addSequential(TimedCommand((dropSecondCubePath.pathDuration - 3.0).coerceAtLeast(0.001)))
                            addSequential(ElevatorPresetCommand(ElevatorPreset.BEHIND_LIDAR), 3.0)
                        })
                        addParallel(commandGroup {
                            addSequential(object : Command() {
                                override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                            })
                            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.40, timeout = 0.50))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                })
            })

            /*
        Pickup 3rd Cube
         */
            addSequential(commandGroup {
                addSequential(commandGroup {
                    addParallel(ElevatorPresetCommand(ElevatorPreset.INTAKE))
                    addParallel(IntakeCommand(IntakeDirection.IN, speed = 1.0, timeout = 5.0))
                    addParallel(object : MotionProfileCommand("LS-LL", "Pickup Third Cube", pathMirrored = scaleOwnedSide == MatchData.OwnedSide.RIGHT) {

                        var startTime: Long = 0L

                        override fun initialize() {
                            super.initialize()
                            startTime = System.currentTimeMillis()
                        }

                        override fun isFinished() = super.isFinished() || ((System.currentTimeMillis() - startTime > 750) && IntakeSubsystem.isCubeIn)
                    })
                })
                addSequential(IntakeHoldCommand(), 0.001)
            })

            /*
             Drop 3rd Cube in Scale
              */
            addSequential(commandGroup {
                val dropThirdCubePath = MotionProfileCommand("LS-LL", "Pickup Third Cube", robotReversed = true, pathReversed = true, pathMirrored = scaleOwnedSide == MatchData.OwnedSide.RIGHT)
                addParallel(dropThirdCubePath)
                addParallel(commandGroup {
                    addSequential(commandGroup {
                        addParallel(commandGroup {
                            addSequential(TimedCommand((dropThirdCubePath.pathDuration - 3.0).coerceAtLeast(0.001)))
                            addSequential(ElevatorPresetCommand(ElevatorPreset.BEHIND_LIDAR), 3.0)
                        })
                        addParallel(commandGroup {
                            addSequential(object : Command() {
                                override fun isFinished() = ArmSubsystem.currentPosition > ArmPosition.BEHIND.ticks - 100
                            })
                            addSequential(IntakeCommand(IntakeDirection.OUT, speed = 0.35, timeout = 0.50))
                            addSequential(IntakeHoldCommand(), 0.001)
                        })
                    })
                })
            })

            addSequential(ElevatorPresetCommand(ElevatorPreset.INTAKE))

        }
    }
}

/**
 * Stores starting position of the robot.
 */
enum class StartingPositions {
    LEFT, CENTER, RIGHT;
}

enum class AutoModes(val numCubes: String) {
    FULL("2.5 / 3"), SIMPLE("1"), SWITCH("0 / 1"), CARRY_ALLIANCE("0 / 1 / 2"), BASELINE("0");
}
