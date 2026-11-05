package com.luzart.tubesach.carapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Xe đang đỗ hay đang chạy. */
enum class DriveState {
    /** Chưa biết (vừa kết nối) – coi như đang chạy. */
    UNKNOWN,
    PARKED,
    MOVING,
    /** Chưa cho phép app đọc tốc độ xe / vị trí. */
    NO_PERMISSION,
    /** Không đọc được tốc độ (xe không gửi, điện thoại không bắt được GPS). */
    UNAVAILABLE,
}

/**
 * Chỉ cho hiện hình khi xe đứng yên – giống quy định của Android Auto với ứng dụng video.
 *
 * Lấy tốc độ theo thứ tự: tốc độ xe gửi qua Android Auto → GPS của xe → GPS của điện thoại.
 * Không đọc được gì thì coi như đang chạy (an toàn trước): chỉ phát tiếng.
 */
class ParkingGate(private val carContext: CarContext) {

    private val _state = MutableStateFlow(DriveState.UNKNOWN)
    val state: StateFlow<DriveState> = _state.asStateFlow()

    private val logic = SpeedLogic()
    private var started = false
    private val startedAt = SystemClock.elapsedRealtime()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = ContextCompat.getMainExecutor(carContext)

    /** Tốc độ xe (m/giây) gần nhất. Xe chỉ gửi khi tốc độ thay đổi nên giá trị cũ vẫn đúng. */
    private var carSpeed: Float? = null
    /** Tốc độ theo GPS và thời điểm nhận (GPS cũ quá 5 giây thì bỏ). */
    private var gpsSpeed: Float? = null
    private var gpsAt = 0L

    private var carSpeedOn = false
    private var carLocationOn = false
    private var phoneGpsOn = false

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val raw = speed.rawSpeedMetersPerSecond
        carSpeed = if (raw.status == CarValue.STATUS_SUCCESS) raw.value else null
        evaluate()
    }

    private val carLocationListener = OnCarDataAvailableListener<CarHardwareLocation> { loc ->
        val v = loc.location
        if (v.status == CarValue.STATUS_SUCCESS) v.value?.let(::onLocation)
    }

    private val phoneLocationListener = LocationListener { onLocation(it) }

    private val tick = object : Runnable {
        override fun run() {
            evaluate()
            handler.postDelayed(this, 1_000)
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(carContext, p) == PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean = granted(PERMISSION_SPEED) || granted(PERMISSION_LOCATION)

    /** Còn quyền nào chưa cho (để hiện nút "Bật hình" khi chưa đọc được tốc độ). */
    fun missingPermission(): Boolean = !granted(PERMISSION_SPEED) || !granted(PERMISSION_LOCATION)

    fun start() {
        if (!hasPermission()) {
            _state.value = DriveState.NO_PERMISSION
            return
        }
        val hardware = runCatching { carContext.getCarService(CarHardwareManager::class.java) }.getOrNull()
        if (!carSpeedOn && granted(PERMISSION_SPEED) && hardware != null) {
            carSpeedOn = runCatching { hardware.carInfo.addSpeedListener(executor, speedListener) }.isSuccess
        }
        if (granted(PERMISSION_LOCATION)) {
            if (!carLocationOn && hardware != null) {
                carLocationOn = runCatching {
                    hardware.carSensors.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_NORMAL, executor, carLocationListener)
                }.isSuccess
            }
            if (!phoneGpsOn) {
                phoneGpsOn = runCatching {
                    val lm = carContext.getSystemService(LocationManager::class.java)
                    @Suppress("MissingPermission")
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, phoneLocationListener, Looper.getMainLooper())
                }.isSuccess
            }
        }
        if (!started) {
            started = true
            _state.value = DriveState.UNKNOWN
            handler.post(tick)
        }
    }

    /** Hỏi quyền đọc tốc độ xe và vị trí (bảng hỏi hiện trên điện thoại hoặc màn hình xe). */
    fun requestPermission(onDone: () -> Unit = {}) {
        try {
            carContext.requestPermissions(listOf(PERMISSION_SPEED, PERMISSION_LOCATION)) { _, _ ->
                start()
                onDone()
            }
        } catch (e: Exception) {
            onDone()
        }
    }

    fun stop() {
        val hardware = runCatching { carContext.getCarService(CarHardwareManager::class.java) }.getOrNull()
        if (carSpeedOn) runCatching { hardware?.carInfo?.removeSpeedListener(speedListener) }
        if (carLocationOn) runCatching { hardware?.carSensors?.removeCarHardwareLocationListener(carLocationListener) }
        if (phoneGpsOn) runCatching { carContext.getSystemService(LocationManager::class.java).removeUpdates(phoneLocationListener) }
        carSpeedOn = false
        carLocationOn = false
        phoneGpsOn = false
        started = false
        handler.removeCallbacks(tick)
    }

    private fun onLocation(loc: Location) {
        if (!loc.hasSpeed()) return
        gpsSpeed = loc.speed
        gpsAt = SystemClock.elapsedRealtime()
        evaluate()
    }

    private fun evaluate() {
        val now = SystemClock.elapsedRealtime()
        val gps = gpsSpeed?.takeIf { now - gpsAt <= GPS_MAX_AGE_MS }
        _state.value = logic.update(carSpeed ?: gps, now - startedAt)
    }

    companion object {
        const val PERMISSION_SPEED = "com.google.android.gms.permission.CAR_SPEED"
        const val PERMISSION_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        private const val GPS_MAX_AGE_MS = 5_000L
    }
}

/**
 * Quyết định đỗ/chạy từ tốc độ (m/giây), có độ trễ để không nhấp nháy khi xe nhích từng chút:
 * - chạy: ≥ 5 km/h;
 * - đỗ: ≤ 1 km/h liên tục ít nhất 3 giây.
 */
class SpeedLogic {
    private var state = DriveState.UNKNOWN
    private var slowSince: Long? = null
    private var lastValidAt: Long? = null

    /** [speedMps] = null khi xe không gửi được tốc độ; [nowMs] = thời gian (ms) từ lúc bắt đầu theo dõi. */
    fun update(speedMps: Float?, nowMs: Long): DriveState {
        if (speedMps == null || speedMps.isNaN()) {
            slowSince = null
            val last = lastValidAt
            state = if (last == null && nowMs > UNAVAILABLE_AFTER_MS) DriveState.UNAVAILABLE
            else if (last != null && nowMs - last > UNAVAILABLE_AFTER_MS) DriveState.UNAVAILABLE
            else if (state == DriveState.PARKED) DriveState.UNKNOWN else state
            return state
        }
        lastValidAt = nowMs
        val speed = kotlin.math.abs(speedMps)
        when {
            speed >= MOVING_MPS -> {
                state = DriveState.MOVING
                slowSince = null
            }
            speed <= PARKED_MPS -> {
                val since = slowSince ?: nowMs.also { slowSince = it }
                if (nowMs - since >= PARKED_HOLD_MS) state = DriveState.PARKED
                else if (state != DriveState.PARKED) state = if (state == DriveState.UNAVAILABLE) DriveState.UNKNOWN else state
            }
            else -> slowSince = null // 1–5 km/h: giữ nguyên trạng thái
        }
        return state
    }

    companion object {
        const val MOVING_MPS = 5f / 3.6f
        const val PARKED_MPS = 1f / 3.6f
        const val PARKED_HOLD_MS = 3_000L
        const val UNAVAILABLE_AFTER_MS = 8_000L
    }
}
