package com.banking.systemdesign.autocomplete;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Baseline Trie (Prefix Tree) implementation of AutocompleteService.
 * Efficiently matches prefixes in O(prefix_length) time.
 */
@Component
public class TrieAutocompleteService implements AutocompleteService {

    private final TrieNode root = new TrieNode();

    public TrieAutocompleteService() {
        // Seed default banking suggestions
        List<String> defaultTerms = List.of(
                "Electricity - City Power",
                "Electricity - National Grid",
                "Water - Municipal Water",
                "Internet - Metro Fiber",
                "Mobile - TeleCorp Postpaid",
                "Alice Smith (ACC-1001)",
                "Bob Jones (ACC-1002)",
                "Charlie Brown (ACC-2001)",
                "Diana Prince (ACC-2002)"
        );
        for (String term : defaultTerms) {
            insert(term);
        }
    }

    @Override
    public synchronized void insert(String term) {
        if (term == null || term.isBlank()) {
            return;
        }
        TrieNode current = root;
        for (char c : term.toLowerCase().toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        current.isEndOfWord = true;
        current.originalTerm = term;
    }

    @Override
    public synchronized List<String> suggest(String prefix, int limit) {
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

        List<String> results = new ArrayList<>();
        collectSuggestions(current, results, limit <= 0 ? 5 : limit);
        return results;
    }

    private void collectSuggestions(TrieNode node, List<String> results, int limit) {
        if (node == null || results.size() >= limit) {
            return;
        }
        if (node.isEndOfWord && node.originalTerm != null) {
            results.add(node.originalTerm);
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
        String originalTerm;
    }
}
