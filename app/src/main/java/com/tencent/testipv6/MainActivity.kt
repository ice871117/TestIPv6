package com.tencent.testipv6

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var resultText: TextView
    private lateinit var envText: TextView
    private lateinit var searchEdit: EditText
    private var lastQuery: Job? = null
    private var lastDetect: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

        setContentView(R.layout.activity_main)
        searchEdit = findViewById(R.id.search_edit)
        resultText = findViewById(R.id.result_text)
        envText = findViewById(R.id.ip_env_text)

        findViewById<Button>(R.id.go).setOnClickListener { doResolve(searchEdit.text.toString()) }
        searchEdit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ENTER -> {
                        doResolve(searchEdit.text.toString())
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        findViewById<Button>(R.id.detect).setOnClickListener { doDetectNetEnv() }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun doResolve(toSearch: String) {
        resultText.text = "resolving..."
        lastQuery?.cancel()
        lastQuery = launch(Dispatchers.Default) {
            val ret = IPEnvironment.resolveDomain(toSearch)
            withContext(Main) {
                resultText.text = ret
            }
        }
    }

    private fun doDetectNetEnv() {
        envText.text = "detecting..."
        lastDetect?.cancel()
        lastDetect = launch(Dispatchers.Default) {
            val ret = IPEnvironment.getLocalNetEnvironment("www.henanga.gov.cn")
            withContext(Main) {
                envText.text = ret.toString()
            }
        }
    }
}
