@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.content.*
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import android.provider.MediaStore
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.hook.SplashHook
import me.iacn.biliroaming.utils.CheckVersionTask
import me.iacn.biliroaming.utils.OnTaskReturn
import me.iacn.biliroaming.utils.hookAfterMethod
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.system.exitProcess


class SettingDialog(context: Context) : AlertDialog.Builder(context) {

    class PrefsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, OnTaskReturn<JSONObject> {
        private lateinit var prefs: SharedPreferences
        private var counter: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "biliroaming"
            addPreferencesFromResource(R.xml.prefs_setting)
            prefs = preferenceManager.sharedPreferences
            if (!prefs.getBoolean("hidden", false)) {
                val hiddenGroup = findPreference("hidden_group") as PreferenceCategory
                preferenceScreen.removePreference(hiddenGroup)
            }
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("version").onPreferenceClickListener = this
            findPreference("custom_splash").onPreferenceChangeListener = this
            findPreference("custom_splash_logo").onPreferenceChangeListener = this
            findPreference("custom_cdn").onPreferenceChangeListener = this
            findPreference("save_log").summary = moduleRes.getString(R.string.save_log_summary).format(XposedInit.logFile.absolutePath)
            findPreference("thanks").onPreferenceClickListener = this
            findPreference("custom_backup").onPreferenceClickListener = this
            checkCompatibleVersion()
            CheckVersionTask(this).execute(URL(moduleRes.getString(R.string.version_url)))
        }

        private fun checkCompatibleVersion() {
            val packageName = AndroidAppHelper.currentPackageName()
            val versionCode = XposedInit.currentContext.packageManager.getPackageInfo(packageName, 0).versionCode
            var supportLiveHook = false
            var supportAdd4K = false
            var supportMusicNotificationHook = true
            val supportFullSplash = try {
                instance.splashInfoClass?.getMethod("getMode") != null
            } catch (e: Throwable) {
                false
            }
            val supportMain = !XposedInit.isBuiltIn || !XposedInit.is64 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            when (packageName) {
                "com.bilibili.app.in" -> {
                    when (versionCode) {
                        in 2050410..2080109 -> supportLiveHook = true
                    }
                    supportAdd4K = true
                }
                "com.bilibili.app.blue" -> if (versionCode < 6080000)
                    supportAdd4K = true
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                supportMusicNotificationHook = false
            val supportSplashHook = instance.brandSplashClass != null
            val supportDrawer = instance.homeUserCenterClass != null
            if (!supportDrawer)
                disablePreference("drawer")
            if (!supportSplashHook) {
                disablePreference("custom_splash")
                disablePreference("custom_splash_logo")
            }
            if (!supportFullSplash) {
                disablePreference("full_splash")
            }
            if (!supportLiveHook) {
                disablePreference("add_live")
            }
            if (!supportAdd4K) {
                disablePreference("add_4k")
            }
            if (!supportMusicNotificationHook) {
                disablePreference("music_notification", moduleRes.getString(R.string.os_not_support))
            }
            if (!supportMain) {
                disablePreference("main_func", "Android O以下系统不支持64位Xpatch版，请使用32位版")
            }

        }

        private fun disablePreference(name: String, message: String = moduleRes.getString(R.string.not_support)) {
            findPreference(name).run {
                isEnabled = false
                summary = message
                if (this is SwitchPreference) this.isChecked = false
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "custom_splash" -> {
                    if (newValue as Boolean)
                        selectImage(SPLASH_SELECTION)
                }
                "custom_splash_logo" -> {
                    if (newValue as Boolean)
                        selectImage(LOGO_SELECTION)
                }
                "custom_cdn" -> {
                    if (!(newValue as String).matches(Constant.CDN_REGEX)) {
                        toastMessage("无效输入")
                        return false
                    }
                }
            }
            return true
        }

        private fun selectImage(action: Int): Boolean {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            try {
                startActivityForResult(Intent.createChooser(intent, "选择一张图片"), action)
            } catch (ex: ActivityNotFoundException) {
                toastMessage("请安装文件管理器")
            }
            return true
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            val destFile = when (requestCode) {
                SPLASH_SELECTION ->
                    File(XposedInit.currentContext.filesDir, SplashHook.SPLASH_IMAGE)
                LOGO_SELECTION ->
                    File(XposedInit.currentContext.filesDir, SplashHook.LOGO_IMAGE)
                else -> null
            } ?: return
            val uri = data?.data
            if (resultCode == RESULT_CANCELED || uri == null) {
                destFile.delete()
                return
            }
            val stream = ByteArrayOutputStream()
            stream.flush()
            MediaStore.Images.Media.getBitmap(activity.contentResolver, uri).compress(Bitmap.CompressFormat.PNG, 100, stream)
            val dest = FileOutputStream(destFile)
            stream.writeTo(dest)
            super.onActivityResult(requestCode, resultCode, data)
        }


        private fun onVersionClick(): Boolean {
            if (prefs.getBoolean("hidden", false) || counter == 7) {
                return true
            }
            counter++
            if (counter == 7) {
                prefs.edit()?.putBoolean("hidden", true)?.apply()
                toastMessage("已开启隐藏功能，重启应用生效")
            } else if (counter >= 4) {
                toastMessage("再按${7 - counter}次开启隐藏功能")
            }

            return true
        }

        override fun onReturn(result: JSONObject?) {
            try {
                result?.getString("name")?.let {
                    if (BuildConfig.VERSION_NAME != it) {
                        findPreference("version").summary = "${BuildConfig.VERSION_NAME}（最新版${it}）"
                        val aboutGroup = findPreference("about") as PreferenceCategory
                        val updatePreference = Preference(activity)
                        updatePreference.key = "update"
                        updatePreference.title = moduleRes.getString(R.string.update_title)
                        val log = result.optString("body").substringAfterLast("更新日志")
                        updatePreference.summary = if (log.isNotEmpty()) log else moduleRes.getString(R.string.update_summary)
                        updatePreference.onPreferenceClickListener = this
                        updatePreference.order = 1
                        aboutGroup.addPreference(updatePreference)
                    }
                }
            } catch (e: Throwable) {
            }
        }

        private fun onUpdateClick(): Boolean {
            val uri = Uri.parse(moduleRes.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onThanksClick(): Boolean {
            AlertDialog.Builder(activity).run {
                setTitle("赞助服务器提供者(不是模块作者)")
                setItems(arrayOf("BiliPlus (主服务器)", "kghost (备用服务器)")) { _: DialogInterface, i: Int ->
                    when (i) {
                        0 -> {
                            AlertDialog.Builder(activity).create().run {
                                setTitle("BiliPlus赞助")
                                setView(ImageView(activity).apply {
                                    setImageDrawable(moduleRes.getDrawable(R.drawable.biliplus_alipay))
                                    setOnClickListener {
                                        val uri = Uri.parse(moduleRes.getString(R.string.biliplus_alipay))
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        startActivity(intent)
                                    }
                                }, 50, 50, 50, 50)
                                setButton(AlertDialog.BUTTON_NEGATIVE, "关闭") { _, _ -> }
                                show()
                            }
                        }
                        1 -> {
                        }
                    }
                }
                setNegativeButton("关闭", null)
                show()
            }
            return true
        }

        private fun onCustomBackupClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val layout = moduleRes.getLayout(R.layout.cutomize_backup_dialog)
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(layout, null)
                val editTexts = arrayOf(
                        view.findViewById<EditText>(R.id.cn_server),
                        view.findViewById(R.id.hk_server),
                        view.findViewById(R.id.tw_server),
                        view.findViewById(R.id.sg_server))
                editTexts.forEach { it.setText(prefs.getString(it.tag.toString(), "")) }
                setTitle("自定义备份服务器")
                setView(view)
                setPositiveButton("确定") { _, _ ->
                    editTexts.forEach {
                        val host = it.text.toString()
                        if (host.isNotEmpty())
                            prefs.edit().putString(it.tag.toString(), host).apply()
                        else
                            prefs.edit().remove(it.tag.toString()).apply()
                    }
                }
                show()
            }
            return true
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            return when (preference.key) {
                "version" -> onVersionClick()
                "update" -> onUpdateClick()
                "thanks" -> onThanksClick()
                "custom_backup" -> onCustomBackupClick()
                else -> false
            }
        }
    }

    init {
        val activity = context as Activity
        val backupRes = replaceResource(activity, moduleRes)
        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        val unhook = Preference::class.java.hookAfterMethod("onCreateView", ViewGroup::class.java) { param ->
            if (PreferenceCategory::class.java.isInstance(param.thisObject) && TextView::class.java.isInstance(param.result)) {
                val textView = param.result as TextView
                if (textView.textColors.defaultColor == -13816531)
                    textView.setTextColor(Color.GRAY)
            }
        }

        setView(prefsFragment.view)
        setTitle("哔哩漫游设置")
        restoreResource(activity, backupRes)
        setNegativeButton("返回") { _, _ ->
            unhook?.unhook()
        }
        setPositiveButton("确定并重启客户端") { _, _ ->
            unhook?.unhook()
            restartApplication(activity)
        }
    }

    class BackupRes(val res: Resources, val theme: Resources.Theme)

    companion object {
        @JvmStatic
        fun restartApplication(activity: Activity) {
            // https://stackoverflow.com/a/58530756
            val pm = activity.packageManager
            val intent = pm.getLaunchIntentForPackage(activity.packageName)
            activity.finishAffinity()
            activity.startActivity(intent)
            exitProcess(0)
        }


        @JvmStatic
        fun replaceResource(context: Activity?, res: Resources?): BackupRes? {
            context ?: return null
            res ?: return null
            val resField = ContextThemeWrapper::class.java.getDeclaredField("mResources")
            resField.isAccessible = true
            val oldRes = resField.get(context) as Resources
            val themeField = ContextThemeWrapper::class.java.getDeclaredField("mTheme")
            themeField.isAccessible = true
            val oldTheme = themeField.get(context) as Resources.Theme
            val newTheme = res.newTheme()
            newTheme.applyStyle(R.style.MainTheme, true)
            resField.set(context, res)
            themeField.set(context, newTheme)
            return BackupRes(oldRes, oldTheme)
        }

        @JvmStatic
        fun restoreResource(context: Activity?, backupRes: BackupRes?) {
            backupRes ?: return
            val resField = ContextThemeWrapper::class.java.getDeclaredField("mResources")
            resField.isAccessible = true
            resField.set(context, backupRes.res)
            val themeField = ContextThemeWrapper::class.java.getDeclaredField("mTheme")
            themeField.isAccessible = true
            themeField.set(context, backupRes.theme)
        }

        const val SPLASH_SELECTION = 0
        const val LOGO_SELECTION = 1
    }

}
