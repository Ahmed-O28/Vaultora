package com.banking.systemdesign.autocomplete;

import com.banking.dto.AutocompleteSuggestion;

import java.util.*;

/**
 * Prefix Tree (Trie) specialized for domain banking suggestions (beneficiaries, billers).
 * Supports case-insensitive prefix searching in O(prefix_length) time.
 */
public class BankingTrie {

    private final TrieNode root = new TrieNode();

    public synchronized void insert(String term, AutocompleteSuggestion suggestion) {
        if (term == null || term.isBlank()) {
            return;
        }
        TrieNode current = root;
        for (char c : term.toLowerCase().toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        current.isEndOfWord = true;
        current.suggestions.add(suggestion);
    }

    public synchronized List<AutocompleteSuggestion> searchPrefix(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        TrieNode current = root;
        for (char c : prefix.toLowerCase().toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return Collections.emptyList(); // Prefix not found
            }
        }

        List<AutocompleteSuggestion> results = new ArrayList<>();
        collectSuggestions(current, results, limit <= 0 ? 5 : limit);
        return results;
    }

    private void collectSuggestions(TrieNode node, List<AutocompleteSuggestion> results, int limit) {
        if (node == null || results.size() >= limit) {
            return;
        }
        if (node.isEndOfWord) {
            for (AutocompleteSuggestion s : node.suggestions) {
                if (results.size() >= limit) break;
                // Avoid duplicate suggestions in same search
                if (!results.contains(s)) {
                    results.add(s);
                }
            }
        }
        for (TrieNode child : node.children.values()) {
            collectSuggestions(child, results, limit);
            if (results.size() >= limit) {
                break;
            }
        }
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new TreeMap<>();
        boolean isEndOfWord = false;
        List<AutocompleteSuggestion> suggestions = new ArrayList<>();
    }
}
