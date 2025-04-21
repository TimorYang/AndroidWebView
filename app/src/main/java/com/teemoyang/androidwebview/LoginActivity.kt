package com.teemoyang.androidwebview

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var employeeTab: TextView
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

    // 当前选中的标签
    private var currentTab = TAB_EMPLOYEE

    companion object {
        private const val TAB_EMPLOYEE = 0
        private const val TAB_VISITOR = 1
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 初始化UI组件
        initUI()
        // 设置点击事件
        setupListeners()
    }

    private fun initUI() {
        // 选项卡和指示器
        employeeTab = findViewById(R.id.employeeTab)
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

        // 初始显示内部员工表单
        showEmployeeTab()
    }

    private fun setupListeners() {
        // 选项卡切换
        employeeTab.setOnClickListener {
            if (currentTab != TAB_EMPLOYEE) {
                showEmployeeTab()
            }
        }

        visitorTab.setOnClickListener {
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
            // 直接进入导航界面，不需要登录
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun showEmployeeTab() {
        // 更新UI
        employeeTab.setTextColor(resources.getColor(R.color.tab_selected_text, null))
        employeeTab.setTypeface(null, android.graphics.Typeface.BOLD)
        visitorTab.setTextColor(resources.getColor(R.color.tab_unselected_text, null))
        visitorTab.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // 更新背景
        employeeTab.setBackgroundResource(R.drawable.tab_selected_background)
        visitorTab.setBackgroundResource(R.drawable.tab_unselected_background)
        
        // 更新指示器
        employeeTabIndicator.visibility = View.VISIBLE
        visitorTabIndicator.visibility = View.INVISIBLE

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
        visitorTab.setBackgroundResource(R.drawable.tab_selected_background)
        employeeTab.setBackgroundResource(R.drawable.tab_unselected_background)
        
        // 更新指示器
        visitorTabIndicator.visibility = View.VISIBLE
        employeeTabIndicator.visibility = View.INVISIBLE

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
} 