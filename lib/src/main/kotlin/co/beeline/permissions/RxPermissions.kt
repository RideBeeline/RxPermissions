package co.beeline.permissions

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*

/**
 * Observe Android runtime permissions

 * Note: This class should be a singleton.
 */
class RxPermissions(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 8712
    }

    private val subjects: MutableMap<String, BehaviorSubject<Boolean>> = HashMap(6)

    /**
     * @return an observable that emits the state changes for a given permission
     */
    @MainThread
    fun observe(permission: String): Observable<Boolean> {
        return subjectForPermission(permission).asObservable()
    }

    /**
     * @return an observable that completes when a given permission is granted
     */
    @MainThread fun granted(permission: String): Observable<Boolean> {
        return observe(permission).filter { it == true }.take(1)
    }

    @MainThread fun request(activity: Activity, permission: String): Observable<Boolean> {
        return request(permission) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), REQUEST_CODE)
        }
    }

    @MainThread fun request(fragment: Fragment, permission: String): Observable<Boolean> {
        return request(permission) {
            fragment.requestPermissions(arrayOf(permission), REQUEST_CODE)
        }
    }

    @MainThread private fun request(permission: String, request: () -> Unit): Observable<Boolean> {
        return if (hasPermission(permission)) {
            Observable.just(true)
        } else {
            observe(permission).skip(1).take(1).doOnSubscribe {
                request()
            }
        }
    }

    @MainThread fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == REQUEST_CODE) {
            for (i in 0 until permissions.size) {
                subjectForPermission(permissions[i])
                        .onNext(grantResults[i] == PackageManager.PERMISSION_GRANTED)
            }
            return true
        }
        return false
    }

    fun hasPermission(permission: String): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isGranted(permission)
    }

    private fun subjectForPermission(permission: String): BehaviorSubject<Boolean> {
        var subject: BehaviorSubject<Boolean>? = subjects[permission]
        if (subject == null) {
            subject = BehaviorSubject.create(hasPermission(permission))
            subjects.put(permission, subject)
            return subject
        }
        return subject
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun isGranted(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

