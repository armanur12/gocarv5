package kz.qoldau.catcher

import android.app.DatePickerDialog
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var dateText: TextView
    private lateinit var intervalSpinner: Spinner
    private var targetDate: String? = null
    private var running = false
    private val intervals = listOf(3, 5, 10, 15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        dateText = findViewById(R.id.dateText)
        intervalSpinner = findViewById(R.id.intervalSpinner)

        intervalSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            intervals.map { "$it сек." }
        )

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        CookieManager.getInstance().setAcceptCookie(true)
        webView.loadUrl("https://https://cgr.qoldau.kz/ru/start/")

        findViewById<Button>(R.id.dateButton).setOnClickListener { chooseDate() }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (targetDate == null) {
                statusText.text = "Сначала выберите дату."
                return@setOnClickListener
            }
            running = true
            statusText.text = "Ловля включена: $targetDate"
            scheduleCheck()
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            running = false
            statusText.text = "Остановлено"
        }
    }

    private fun chooseDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            targetDate = String.format(Locale.US, "%02d.%02d.%04d", day, month + 1, year)
            dateText.text = "Целевая дата: $targetDate"
            statusText.text = "Дата зафиксирована. Время не выбирается."
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleCheck() {
        if (!running) return
        val seconds = intervals[intervalSpinner.selectedItemPosition]
        webView.postDelayed({
            if (running) {
                checkPage()
                scheduleCheck()
            }
        }, seconds * 1000L)
    }

    private fun checkPage() {
        val date = targetDate ?: return
        webView.evaluateJavascript(buildCatcherScript(date)) { result ->
            if (result != "null") statusText.text = "Проверка: $result"
        }
    }

    private fun buildCatcherScript(date: String): String {
        val escaped = date.replace("\\", "\\\\").replace("'", "\\'")
        return """
        (function() {
          const TARGET = '$escaped';
          const norm = s => (s || '').replace(/\s+/g,' ').trim();
          function dateMatches(el) {
            let p = el;
            for (let i=0; i<5 && p; i++, p=p.parentElement)
              if (norm(p.innerText).includes(TARGET)) return true;
            return false;
          }
          function available(el) {
            const t = norm(el.innerText).toLowerCase();
            if (!t) return false;
            if (['забронировано','недоступно','занято','нет мест'].some(x => t.includes(x))) return false;
            return /свобод|доступ|выбрат|доступен/.test(t) || el.tagName === 'BUTTON';
          }
          const els = Array.from(document.querySelectorAll(
            'button,[role="button"],a,td,.slot,[class*="slot"],[class*="time"]'
          ));
          for (const el of els) {
            if (!dateMatches(el) || !available(el)) continue;
            if (!dateMatches(el)) continue;
            el.click();
            setTimeout(() => {
              if (!norm(document.body.innerText).includes(TARGET)) return;
              const bs = Array.from(document.querySelectorAll('button,input[type="button"],input[type="submit"]'));
              const save = bs.find(b => /сохранить|save/i.test(norm(b.innerText || b.value)));
              if (save && !save.disabled) save.click();
            }, 300);
            return 'Найден свободный слот на ' + TARGET;
          }
          return 'Свободного слота на ' + TARGET + ' пока нет';
        })();
        """.trimIndent()
    }
}
