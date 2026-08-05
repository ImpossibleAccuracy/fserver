package com.fserver.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.library.core.MathOperation
import com.fserver.library.core.calc

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContent {
            FServerTheme {
                // FServerApp()

                Scaffold { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        var num1 by remember { mutableStateOf("") }
                        var num2 by remember { mutableStateOf("") }
                        var selectedOperation by remember { mutableStateOf<MathOperation?>(null) }

                        var result by remember { mutableStateOf<String?>(null) }

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = num1,
                            onValueChange = { num1 = it },
                            label = { Text("Number 1") },
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = num2,
                            onValueChange = { num2 = it },
                            label = { Text("Number 2") },
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MathOperation.entries.forEach {
                                FilterChip(
                                    selected = selectedOperation == it,
                                    onClick = {
                                        if (selectedOperation == it) {
                                            selectedOperation = null
                                        } else {
                                            selectedOperation = it
                                        }
                                    },
                                    label = { Text(text = it.name) },
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val num1Float = num1.toFloatOrNull()
                                val num2Float = num2.toFloatOrNull()
                                val operation = selectedOperation

                                if (num1Float == null || num2Float == null || operation == null) {
                                    result = "Invalid input"
                                } else {
                                    val resultNumber = calc(
                                        a = num1Float,
                                        b = num2Float,
                                        operation = operation,
                                    )

                                    result = "Result is $resultNumber"
                                }
                            }
                        ) {
                            Text(text = "Calculate")
                        }

                        result?.let {
                            Text(text = it)
                        }
                    }
                }
            }
        }
    }
}
