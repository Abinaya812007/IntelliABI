package com.vlsi.chatbot.service;

import com.vlsi.chatbot.dto.ChatResponse;
import com.vlsi.chatbot.entity.ChatMessage;
import com.vlsi.chatbot.entity.QAEntry;
import com.vlsi.chatbot.entity.User;
import com.vlsi.chatbot.repository.ChatMessageRepository;
import com.vlsi.chatbot.repository.QAEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ChatService {
    
    @Autowired
    private QAEntryRepository qaEntryRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "what", "how", "why", "when",
            "where", "who", "which", "in", "on", "at", "to", "for", "of", "and", "or",
            "can", "you", "me", "tell", "explain", "describe", "about"
    ));
    
    @Transactional
    public ChatResponse processMessage(String message, User user) {
        if (message == null || message.trim().isEmpty()) {
            return new ChatResponse("Please enter a message.", 0);
        }
        
        String lower = message.toLowerCase().trim();
        String response;
        int matchScore = 0;
        
        // Check for time request
        if (lower.contains("time")) {
            LocalTime time = LocalTime.now();
            response = "🕐 Current Time: " + DateTimeFormatter.ofPattern("HH:mm:ss").format(time);
        }
        // Check for date request
        else if (lower.contains("date")) {
            LocalDate date = LocalDate.now();
            response = "📅 Current Date: " + date.toString();
        }
        // Check for file request
        else if (lower.startsWith("get file ") || lower.startsWith("file ")) {
            response = handleFileRequest(message);
        }
        // Check for greeting
        else if (lower.contains("hello") || lower.contains("hi") || lower.equals("hey")) {
            response = "👋 Hello! I'm your VLSI Chatbot assistant. I can answer questions about VLSI, semiconductors, and chip design. Try asking about:\n" +
                    "• What is VLSI?\n" +
                    "• Moore's Law\n" +
                    "• ASIC vs FPGA\n" +
                    "• Semiconductor fabrication";
        }
        // Check for help
        else if (lower.contains("help") || lower.equals("?")) {
            response = "💡 Here's what I can help you with:\n\n" +
                    "📚 VLSI Topics: Ask about VLSI concepts, fabrication, design flow\n" +
                    "🔧 Semiconductors: P-type, N-type, doping, transistors\n" +
                    "⚡ Technologies: CMOS, ASIC, FPGA, SoC\n" +
                    "📁 Files: Type 'get file <filename>' to read files\n" +
                    "🕐 Time/Date: Ask for current time or date\n\n" +
                    "Try questions like: 'What is Moore's Law?' or 'Difference between ASIC and FPGA'";
        }
        // Search Q&A database
        else {
            QAMatch match = findBestMatch(message);
            if (match != null && match.score > 45) {
                response = "📚 " + match.answer;
                matchScore = match.score;
                if (matchScore < 80) {
                    response += "\n\n(Match confidence: " + matchScore + "%)";
                }
            } else {
                response = "🤔 I couldn't find a good match for your question. Try rephrasing or ask about VLSI topics like:\n" +
                        "• What is VLSI?\n" +
                        "• What is CMOS?\n" +
                        "• Explain Moore's Law\n" +
                        "• What are the steps in VLSI design flow?";
            }
        }
        
        // Save chat message to database
        if (user != null) {
            ChatMessage chatMessage = new ChatMessage(user, message, response, matchScore);
            chatMessageRepository.save(chatMessage);
        }
        
        return new ChatResponse(response, matchScore);
    }
    
    private String handleFileRequest(String message) {
        String[] parts = message.split("\\s+", 3);
        String filename = "";
        
        if (parts.length >= 3) {
            filename = parts[2];
        } else if (parts.length == 2) {
            filename = parts[1];
        }
        
        if (filename.isEmpty()) {
            return "📁 Please specify a filename, e.g., 'get file sample.txt'";
        }
        
        Path filesDir = Paths.get(System.getProperty("user.dir"), "files");
        Path file = filesDir.resolve(filename).normalize();
        
        try {
            if (!file.startsWith(filesDir)) {
                return "⚠️ Invalid filename - path traversal not allowed";
            }
            if (!Files.exists(file)) {
                return "❌ File not found: " + filename;
            }
            long size = Files.size(file);
            if (size > 200_000) {
                return "⚠️ File too large to display (max 200KB)";
            }
            String content = Files.readString(file);
            return "📄 --- File: " + filename + " ---\n" + content;
        } catch (IOException e) {
            return "❌ Error reading file: " + e.getMessage();
        }
    }
    
    private QAMatch findBestMatch(String query) {
        List<QAEntry> entries = qaEntryRepository.findAll();
        if (entries.isEmpty()) {
            return null;
        }
        
        QAMatch best = null;
        int bestScore = 0;
        
        for (QAEntry entry : entries) {
            int score = calculateMatchScore(query, entry.getQuestion());
            if (score > bestScore) {
                bestScore = score;
                best = new QAMatch(entry.getQuestion(), entry.getAnswer(), score);
            }
        }
        
        return best;
    }
    
    private int calculateMatchScore(String query, String target) {
        String q = normalize(query);
        String t = normalize(target);
        
        // Exact match
        if (q.equals(t)) return 100;
        
        // Tokenize
        Set<String> qTokens = new HashSet<>(Arrays.asList(q.split("\\s+")));
        Set<String> tTokens = new HashSet<>(Arrays.asList(t.split("\\s+")));
        
        // Remove common words
        qTokens.removeAll(COMMON_WORDS);
        tTokens.removeAll(COMMON_WORDS);
        
        // Remove empty tokens
        qTokens.remove("");
        tTokens.remove("");
        
        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0;
        
        // Calculate Jaccard similarity
        Set<String> intersection = new HashSet<>(qTokens);
        intersection.retainAll(tTokens);
        
        Set<String> union = new HashSet<>(qTokens);
        union.addAll(tTokens);
        
        int score = (int) (100.0 * intersection.size() / union.size());
        
        // Bonus for substring match
        if (t.contains(q) || q.contains(t)) {
            score = Math.max(score, 85);
        }
        
        // Check partial token matches
        for (String qt : qTokens) {
            for (String tt : tTokens) {
                if (qt.length() >= 4 && tt.contains(qt)) {
                    score += 5;
                }
                if (tt.length() >= 4 && qt.contains(tt)) {
                    score += 5;
                }
            }
        }
        
        return Math.min(100, score);
    }
    
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
    
    public List<ChatMessage> getChatHistory(User user) {
        return chatMessageRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }
    
    @Transactional
    public void clearChatHistory(User user) {
        List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtDesc(user);
        chatMessageRepository.deleteAll(messages);
    }
    
    public long getQACount() {
        return qaEntryRepository.count();
    }
    
    // Inner class for Q&A matching
    private static class QAMatch {
        final String question;
        final String answer;
        final int score;
        
        QAMatch(String question, String answer, int score) {
            this.question = question;
            this.answer = answer;
            this.score = score;
        }
    }
}
