package com.banking.systemdesign.autocomplete;

import java.util.List;

/**
 * Component 8: Autocomplete Service
 * 
 * Responsibility: Suggest matching beneficiaries, billers, and transaction categories in real-time
 * as the user types in search or payment inputs.
 * 
 * Future Production Implementation:
 * Distributed Trie index with top-k frequency scoring and Redis prefix caching.
 */
public interface AutocompleteService {

    /**
     * Inserts a searchable term into the autocomplete index.
     */
    void insert(String term);

    /**
     * Returns top matching suggestions for the given query prefix.
     *
     * @param prefix partial user input
     * @param limit maximum suggestions to return
     * @return list of matching terms
     */
    List<String> suggest(String prefix, int limit);
}
