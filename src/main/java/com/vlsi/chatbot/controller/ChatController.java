package com.vlsi.chatbot.controller;

import com.vlsi.chatbot.dto.ChatRequest;
import com.vlsi.chatbot.dto.ChatResponse;
import com.vlsi.chatbot.entity.ChatMessage;
import com.vlsi.chatbot.entity.User;
import com.vlsi.chatbot.service.ChatService;
import com.vlsi.chatbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;

@RestController
@RequestMapping("/api")
public class ChatController {
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }
        
        ChatResponse response = chatService.processMessage(request.getMessage(), user);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/chat/history")
    public ResponseEntity<List<Map<String, Object>>> getChatHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        
        User user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        
        List<ChatMessage> messages = chatService.getChatHistory(user);
        List<Map<String, Object>> history = new ArrayList<>();
        
        // Reverse to show oldest first
        Collections.reverse(messages);
        
        for (ChatMessage msg : messages) {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", msg.getUserMessage());
            userMsg.put("timestamp", msg.getCreatedAt().toString());
            history.add(userMsg);
            
            Map<String, Object> botMsg = new HashMap<>();
            botMsg.put("role", "assistant");
            botMsg.put("content", msg.getBotResponse());
            botMsg.put("timestamp", msg.getCreatedAt().toString());
            history.add(botMsg);
        }
        
        return ResponseEntity.ok(history);
    }
    
    @DeleteMapping("/chat/history")
    public ResponseEntity<Map<String, Object>> clearChatHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Not authenticated"));
        }
        
        User user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "User not found"));
        }
        
        chatService.clearChatHistory(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Chat history cleared"));
    }
    
    @GetMapping("/qa-count")
    public ResponseEntity<Long> getQACount() {
        return ResponseEntity.ok(chatService.getQACount());
    }
}
