@file:Suppress("DEPRECATION")

package frc.team5190.robot.listener

import edu.wpi.first.wpilibj.networktables.NetworkTable
import edu.wpi.first.wpilibj.tables.ITable
import edu.wpi.first.wpilibj.tables.ITableListener
import frc.team5190.robot.util.DriveConstants
import frc.team5190.robot.util.Maths
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.util.*
import kotlin.collections.HashMap

object Listener : ITableListener {

    private var pathfinderOutputTable: NetworkTable = NetworkTable.getTable("pathfinderOutput")
    private var pathfinderInputTable: NetworkTable = NetworkTable.getTable("pathfinderInput")
    private val leftTrajectories = HashMap<Int, MotionProfileTrajectory>()
    private val rightTrajectories = HashMap<Int, MotionProfileTrajectory>()
    private val localFiles = HashMap<Int, String>()
    private var requestId = 0

    init {
        pathfinderOutputTable.addTableListener(this, true)
    }

    fun requestPath(folder: String, path: String, obstructed: Boolean = false, index: Double = 0.0): Int {
        val id = this.requestId++
        localFiles.put(id, folder + "/" + path)
        pathfinderInputTable.putString("folder_" + id, folder)
        pathfinderInputTable.putString("path_" + id, path)
        pathfinderInputTable.putBoolean("obstructed_" + id, obstructed)
        pathfinderInputTable.putNumber("index_" + id, index)
        pathfinderInputTable.putNumber("request_" + id, id.toDouble())
        NetworkTable.flush()
        return id
    }

    fun getLeftPath(id: Int): MotionProfileTrajectory? {
        var retryCounter = 0
        while (leftTrajectories[id] == null && retryCounter++ < 20) {
            Thread.sleep(50)
        }

        if (leftTrajectories[id] == null) {
            loadFromFiles(id)
        }

        return leftTrajectories[id]
    }

    fun getRightPath(id: Int): MotionProfileTrajectory? {
        var retryCounter = 0
        while (rightTrajectories[id] == null && retryCounter++ < 20) {
            Thread.sleep(50)
        }

        if (rightTrajectories[id] == null) {
            loadFromFiles(id)
        }

        return rightTrajectories[id]
    }

    override fun valueChanged(iTable: ITable, string: String, receivedObject: Any, newValue: Boolean) {
        if (string.startsWith("response_") && newValue) {
            val id:Int = ((receivedObject as Double).toInt())
            val folder = pathfinderOutputTable.getString("folder_" + id, "")
            val path = pathfinderOutputTable.getString("path_" + id, "")
            deserializeTrajectoryArray(id, pathfinderOutputTable.getString("trajectories_" + id, "") as String)

            println("Got path: $id, $folder/$path")

            // clean up the response keys
            pathfinderOutputTable.delete("response_" + id)
            pathfinderOutputTable.delete("folder_" + id)
            pathfinderOutputTable.delete("path_" + id)
            pathfinderOutputTable.delete("trajectories_" + id)
        }
    }

    private fun deserializeTrajectoryArray(id: Int, serializedTrajectoryArray: String) {
        val b = Base64.getDecoder().decode(serializedTrajectoryArray.toByteArray())
        val bi = ByteArrayInputStream(b)
        val si = ObjectInputStream(bi)
        val s = si.readObject() as Array<Array<Array<Double>>>
        leftTrajectories.put(id, deserializeTrajectory(s[0]))
        rightTrajectories.put(id, deserializeTrajectory(s[1]))
    }

    private fun deserializeTrajectory(s: Array<Array<Double>>) : MotionProfileTrajectory {
        val motionArray = mutableListOf<MotionProfileSegment>()
        s.indices.mapTo(motionArray) {
            MotionProfileSegment(s[it][0], s[it][1], s[it][2], s[it][3], s[it][4], s[it][5], s[it][6], s[it][7])
        }
        return motionArray
    }

    private fun loadFromFiles(id: Int) {
        println("Reading $id from backup store")
        leftTrajectories.put(id, loadFromFile(localFiles[id] + " Left Detailed.csv", true))
        rightTrajectories.put(id, loadFromFile(localFiles[id] + " Right Detailed.csv", true))
    }

    private fun loadFromFile(file: String, skipFirst: Boolean = false): MotionProfileTrajectory {
        javaClass.classLoader.getResourceAsStream(file).use { stream ->
            return InputStreamReader(stream).readLines().mapIndexedNotNull { index, string ->
                if(skipFirst && index == 0) return@mapIndexedNotNull null
                val pointData = string.split(",").map { it.trim() }
                return@mapIndexedNotNull MotionProfileSegment(pointData[0].toDouble(), pointData[1].toDouble(), pointData[2].toDouble(), pointData[3].toDouble(), pointData[4].toDouble(), pointData[5].toDouble(), pointData[6].toDouble(), pointData[7].toDouble())
            }
        }
    }
}

typealias MotionProfileTrajectory = List<MotionProfileSegment>

/**
 * Stores trajectory data for each point along the trajectory.
 */
data class MotionProfileSegment(val dt: Double, val x: Double, val y: Double, private val position: Double,
                                private val velocity: Double, val acceleration: Double,
                                val jerk: Double, val heading: Double) {
    // Converts feet and feet/sec into rotations and rotations/sec.
    private val rotations = Maths.feetToRotations(position, DriveConstants.WHEEL_RADIUS)
    private val rpm = Maths.feetPerSecondToRPM(velocity, DriveConstants.WHEEL_RADIUS)

    // Converts rotations and rotations/sec to native units and native units/100 ms.
    var nativeUnits = Maths.rotationsToNativeUnits(rotations, DriveConstants.SENSOR_UNITS_PER_ROTATION)
    val nativeUnitsPer100Ms = Maths.rpmToNativeUnitsPer100Ms(rpm, DriveConstants.SENSOR_UNITS_PER_ROTATION)
    val duration: Int = (dt * 1000).toInt()
}
