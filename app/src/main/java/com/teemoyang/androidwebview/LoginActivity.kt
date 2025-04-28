package com.teemoyang.androidwebview

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.teemoyang.androidwebview.api.ApiClient
import com.teemoyang.androidwebview.data.UserSession
import com.teemoyang.androidwebview.model.LoginRequest
import com.teemoyang.androidwebview.model.LoginResponse
import com.teemoyang.androidwebview.utils.PasswordUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    // UI组件
    private lateinit var employeeTabContainer: RelativeLayout
    private lateinit var employeeTab: TextView
    private lateinit var visitorTabContainer: RelativeLayout
    private lateinit var visitorTab: TextView
    private lateinit var employeeTabIndicator: View
    private lateinit var visitorTabIndicator: View
    private lateinit var employeeLoginForm: LinearLayout
    private lateinit var visitorLoginForm: LinearLayout
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var idCardInput: EditText
    private lateinit var togglePassword: ImageView
    private lateinit var loginButton: Button
    private lateinit var navigationButton: Button
    
    // 底部弹窗
    private lateinit var mapSelectionDialog: BottomSheetDialog

    // 当前选中的标签
    private var currentTab = TAB_EMPLOYEE
    
    // SharedPreferences对象，用于存储登录信息
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAB_EMPLOYEE = 0
        private const val TAB_VISITOR = 1
        private const val TAG = "LoginActivity"
        
        // SharedPreferences相关常量
        private const val PREF_NAME = "LoginPreferences"
        private const val KEY_ROLE_TYPE = "roleType"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password" // 注意：实际应用中不建议明文存储密码
        private const val KEY_VISITOR_NAME = "visitorName"
        private const val KEY_ID_CARD = "idCard"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 检查用户是否已登录，如果已登录则直接进入MainActivity
        if (UserSession.isLoggedIn()) {
            Log.d(TAG, "用户已登录，直接进入主界面")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_login)

        // 初始化UI组件
        initUI()
        
        // 设置点击事件
        setupListeners()
        
        // 加载保存的登录信息
        loadSavedLoginInfo()
    }

    private fun initUI() {
        // 选项卡和指示器
        employeeTabContainer = findViewById(R.id.employeeTabContainer)
        employeeTab = findViewById(R.id.employeeTab)
        visitorTabContainer = findViewById(R.id.visitorTabContainer)
        visitorTab = findViewById(R.id.visitorTab)

        employeeTabIndicator = findViewById(R.id.employeeTabIndicator)
        visitorTabIndicator = findViewById(R.id.visitorTabIndicator)

        // 表单
        employeeLoginForm = findViewById(R.id.employeeLoginForm)
        visitorLoginForm = findViewById(R.id.visitorLoginForm)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        nameInput = findViewById(R.id.nameInput)
        idCardInput = findViewById(R.id.idCardInput)
        togglePassword = findViewById(R.id.togglePassword)

        // 按钮
        loginButton = findViewById(R.id.loginButton)
        navigationButton = findViewById(R.id.navigationButton)
    }

    private fun setupListeners() {
        // 选项卡切换
        employeeTabContainer.setOnClickListener {
            if (currentTab != TAB_EMPLOYEE) {
                showEmployeeTab()
            }
        }

        visitorTabContainer.setOnClickListener {
            if (currentTab != TAB_VISITOR) {
                showVisitorTab()
            }
        }

        // 密码显示切换
        togglePassword.setOnClickListener {
            if (passwordInput.transformationMethod is PasswordTransformationMethod) {
                // 显示密码
                passwordInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                togglePassword.setImageResource(R.drawable.show)
            } else {
                // 隐藏密码
                passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
                togglePassword.setImageResource(R.drawable.hide)
            }
            // 移动光标到末尾
            passwordInput.setSelection(passwordInput.text.length)
        }

        // 登录按钮
        loginButton.setOnClickListener {
            if (validateInputs()) {
                // 验证通过，进行登录
                performLogin()
            }
        }

        // 来访导航按钮
        navigationButton.setOnClickListener {
            // 显示地图选择底部弹窗
            showMapSelectionDialog()
        }
    }

    /**
     * 加载保存的登录信息
     */
    private fun loadSavedLoginInfo() {
        // 获取保存的角色类型
        val savedRoleType = sharedPreferences.getInt(KEY_ROLE_TYPE, TAB_EMPLOYEE)
        
        // 根据保存的角色类型显示相应的标签和表单
        if (savedRoleType == TAB_EMPLOYEE) {
            showEmployeeTab()
            // 填充员工登录信息
            usernameInput.setText(sharedPreferences.getString(KEY_USERNAME, ""))
            passwordInput.setText(sharedPreferences.getString(KEY_PASSWORD, ""))
        } else {
            showVisitorTab()
            // 填充访客登录信息
            nameInput.setText(sharedPreferences.getString(KEY_VISITOR_NAME, ""))
            idCardInput.setText(sharedPreferences.getString(KEY_ID_CARD, ""))
        }
    }
    
    /**
     * 保存登录信息 - 现在默认总是保存
     */
    private fun saveLoginInfo() {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_ROLE_TYPE, currentTab)
        
        if (currentTab == TAB_EMPLOYEE) {
            // 保存员工登录信息
            editor.putString(KEY_USERNAME, usernameInput.text.toString().trim())
            editor.putString(KEY_PASSWORD, passwordInput.text.toString().trim())
        } else {
            // 保存访客登录信息
            editor.putString(KEY_VISITOR_NAME, nameInput.text.toString().trim())
            editor.putString(KEY_ID_CARD, idCardInput.text.toString().trim())
        }
        
        editor.apply()
        Log.d(TAG, "已保存登录信息，角色类型: ${if (currentTab == TAB_EMPLOYEE) "员工" else "访客"}")
    }

    private fun showEmployeeTab() {
        // 更新UI
        employeeTab.setTextColor(resources.getColor(R.color.tab_selected_text, null))
        employeeTab.setTypeface(null, android.graphics.Typeface.BOLD)
        visitorTab.setTextColor(resources.getColor(R.color.tab_unselected_text, null))
        visitorTab.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // 更新背景
        employeeTabContainer.setBackgroundResource(R.drawable.tab_selected_background)
        visitorTabContainer.setBackgroundResource(R.drawable.tab_unselected_background)
        
        // 更新指示器
        employeeTabIndicator.visibility = View.VISIBLE
        visitorTabIndicator.visibility = View.GONE

        // 显示相应表单
        employeeLoginForm.visibility = View.VISIBLE
        visitorLoginForm.visibility = View.GONE

        // 更新当前标签
        currentTab = TAB_EMPLOYEE
    }

    private fun showVisitorTab() {
        // 更新UI
        visitorTab.setTextColor(resources.getColor(R.color.tab_selected_text, null))
        visitorTab.setTypeface(null, android.graphics.Typeface.BOLD)
        employeeTab.setTextColor(resources.getColor(R.color.tab_unselected_text, null))
        employeeTab.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // 更新背景
        visitorTabContainer.setBackgroundResource(R.drawable.tab_selected_background)
        employeeTabContainer.setBackgroundResource(R.drawable.tab_unselected_background)
        
        // 更新指示器
        visitorTabIndicator.visibility = View.VISIBLE
        employeeTabIndicator.visibility = View.GONE

        // 显示相应表单
        visitorLoginForm.visibility = View.VISIBLE
        employeeLoginForm.visibility = View.GONE

        // 更新当前标签
        currentTab = TAB_VISITOR
    }

    private fun validateInputs(): Boolean {
        return when (currentTab) {
            TAB_EMPLOYEE -> {
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()

                if (username.isEmpty()) {
                    Toast.makeText(this, "请输入账号", Toast.LENGTH_SHORT).show()
                    false
                } else if (password.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            }
            TAB_VISITOR -> {
                val name = nameInput.text.toString().trim()
                val idCard = idCardInput.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入姓名", Toast.LENGTH_SHORT).show()
                    false
                } else if (idCard.isEmpty()) {
                    Toast.makeText(this, "请输入身份证号", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            }
            else -> false
        }
    }

    private fun performLogin() {
        // 显示加载状态
        showLoading(true)
        
        when (currentTab) {
            TAB_EMPLOYEE -> {
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                
                // 加密密码
                val encryptedPassword = PasswordUtils.encryptPassword(password)
                
                // 创建员工登录请求
                val loginRequest = LoginRequest(
                    role = "1",  // 1表示员工
                    userName = username,
                    password = encryptedPassword
                )
                
                // 调用登录API
                callLoginApi(loginRequest)
            }
            TAB_VISITOR -> {
                val name = nameInput.text.toString().trim()
                val idCard = idCardInput.text.toString().trim()
                
                // 创建访客登录请求
                val loginRequest = LoginRequest(
                    role = "2",  // 2表示访客
                    nickName = name,
                    idCard = idCard
                )
                
                // 调用登录API
                callLoginApi(loginRequest)
            }
        }
    }
    
    private fun callLoginApi(loginRequest: LoginRequest) {
        ApiClient.apiService.login(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                // 隐藏加载状态
                showLoading(false)
                
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    
                    if (loginResponse?.success == true) {
                        // 登录成功
                        val userData = loginResponse.data
                        
                        if (userData != null) {
                            // 保存用户数据到会话
                            UserSession.saveUserData(userData)
                            
                            // 默认总是保存登录信息
                            saveLoginInfo()
                            
                            val welcomeMessage = when (currentTab) {
                                TAB_EMPLOYEE -> "内部员工登录成功: ${userData.userName}"
                                TAB_VISITOR -> "访客登录成功: ${userData.nickName}"
                                else -> "登录成功"
                            }
                            
                            Toast.makeText(this@LoginActivity, welcomeMessage, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "$welcomeMessage, 数据: $userData")
                            
                            // 跳转到主界面
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()  // 结束登录界面
                        } else {
                            // 用户数据为空
                            Toast.makeText(this@LoginActivity, "登录成功但未获取到用户数据", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 登录失败，显示错误信息
                        val errorMsg = loginResponse?.errorMsg?.firstOrNull() ?: "登录失败，请重试"
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 请求失败
                    Toast.makeText(this@LoginActivity, "网络请求失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // 隐藏加载状态
                showLoading(false)
                
                // 网络错误
                Toast.makeText(this@LoginActivity, "网络错误: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "登录网络错误", t)
            }
        })
    }
    
    private fun showLoading(isLoading: Boolean) {
        // 根据加载状态禁用/启用按钮和输入框
        loginButton.isEnabled = !isLoading
        navigationButton.isEnabled = !isLoading
        
        // 可以添加进度条显示等其他UI交互
        if (isLoading) {
            loginButton.text = "登录中..."
        } else {
            loginButton.text = if (currentTab == TAB_EMPLOYEE) "登录" else "访客登录"
        }
    }
    
    /**
     * 显示地图选择底部弹窗
     */
    private fun showMapSelectionDialog() {
        mapSelectionDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_map_selection, null)
        mapSelectionDialog.setContentView(dialogView)
        
        // 高德地图选项
        dialogView.findViewById<LinearLayout>(R.id.amapOption).setOnClickListener {
            openAmapNavigation()
            mapSelectionDialog.dismiss()
        }
        
        // 百度地图选项
        dialogView.findViewById<LinearLayout>(R.id.baiduMapOption).setOnClickListener {
            openBaiduMapNavigation()
            mapSelectionDialog.dismiss()
        }
        
        // 华为地图选项
        dialogView.findViewById<LinearLayout>(R.id.huaweiMapOption).setOnClickListener {
            openHuaweiMapNavigation()
            mapSelectionDialog.dismiss()
        }
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            mapSelectionDialog.dismiss()
        }
        
        mapSelectionDialog.show()
    }
    
    /**
     * 打开高德地图导航
     * 使用URL Scheme方式拉起高德地图APP导航到北京天安门
     */
    private fun openAmapNavigation() {
        try {
            // 使用高德地图官方示例方式
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            
            // 天安门的经纬度 (116.397428,39.90923)
            // 参数说明：
            // sourceApplication: 应用名称
            // poiname: 目的地名称
            // lat/lon: 终点纬度/经度
            // dev: 是否偏移(0:gps坐标，1:高德坐标)
            // t: t = 0（驾车）= 1（公交）= 2（步行）= 3（骑行）= 4（火车）= 5（长途客车）
            // （骑行仅在V7.8.8以上版本支持）
            val uri = android.net.Uri.parse(
                "amapuri://route/plan/?" +
                "sourceApplication=${getString(R.string.app_name)}" +
                "&dname=鹏城实验室石壁龙园区-南1门" +
                "&dlat=22.627867685889747" +
                "&dlon=113.93163545312994" +
                "&dev=0" +
                "&t=0"
            )
            intent.data = uri
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "未安装高德地图APP", e)
                Toast.makeText(this, "未安装高德地图APP", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开高德地图失败", e)
            Toast.makeText(this, "打开高德地图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开百度地图导航
     * 使用URL Scheme方式拉起百度地图APP导航到北京天安门
     */
    private fun openBaiduMapNavigation() {
        try {
            // 百度地图URI参数说明
            // origin：起点坐标（可选）
            // destination：终点坐标 lat,lng 或者 name（必选）
            // coord_type：坐标类型，可选参数（bd09ll/gcj02/wgs84）
            // mode：导航模式，默认为driving（可选）
            // src：调用来源，开发者名称（必选）
            
            // 天安门坐标（注意：百度地图使用的是bd09ll坐标系）
            // 这里使用的是转换后的百度坐标：116.404, 39.915
            
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            
            val uri = android.net.Uri.parse(
                "baidumap://map/direction?" +
                "destination=name:鹏城实验室石壁龙园区-南1门|latlng:22.627867685889747,113.93163545312994" +
                "&coord_type=wgs84" +
                "&mode=driving" +
                "&src=${getString(R.string.app_name)}"
            )
            intent.data = uri

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "未安装百度地图APP", e)
                Toast.makeText(this, "未安装百度地图APP", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开百度地图失败", e)
            Toast.makeText(this, "打开百度地图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开华为地图导航
     * 使用URL Scheme方式拉起华为地图APP导航到北京天安门
     */
    private fun openHuaweiMapNavigation() {
        try {
            // 华为地图URI参数说明
            // type：调用的服务类型，99代表调起导航
            // sourceApplication：调用来源，应用名称
            // daddr：目标位置的坐标，格式为"纬度,经度"或地名
            // dname：目标位置名称
            // dlat：目标位置纬度（优先于daddr参数中的纬度）
            // dlon：目标位置经度（优先于daddr参数中的经度）
            
            // 天安门坐标（GCJ-02坐标系）：39.90923,116.397428
            
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            
            val uri = android.net.Uri.parse(
                "mapapp://navigation?" +
                "type=99" +
                "&sourceApplication=${getString(R.string.app_name)}" +
                "&dlat=22.627867685889747" +
                "&dlon=113.93163545312994" +
                "&dname=鹏城实验室石壁龙园区-南1门" +
                "&nav_type=0"  // 导航类型，0表示驾车导航
            )
            intent.data = uri
            
            // 尝试直接启动，由于华为地图相对小众，这里直接尝试启动
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "未安装华为地图APP", e)
                Toast.makeText(this, "未安装华为地图APP", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开华为地图失败", e)
            Toast.makeText(this, "打开华为地图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 