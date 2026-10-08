package com.xiaozhi.simple.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaozhi.simple.model.Message
import com.xiaozhi.simple.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Message item component
 */
@Composable
fun MessageItem(message: Message) {
    val isUser = message.type == MessageType.USER
    val isSystem = message.type == MessageType.SYSTEM
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = when {
                            isSystem -> Color(0xFFE0E0E0)
                            isUser -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFFF5F5F5)
                        },
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Timestamp
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(if (isUser) Alignment.End else Alignment.Start)
            )
        }
        
        if (isUser) Spacer(modifier = Modifier.width(8.dp))
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
