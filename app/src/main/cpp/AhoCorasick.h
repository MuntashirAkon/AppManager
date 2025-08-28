// SPDX-License-Identifier: GPL-3.0-or-later
#ifndef MUNTASHIRAKON_AHOCORASICK_H
#define MUNTASHIRAKON_AHOCORASICK_H

#include <vector>
#include <map>

struct TrieNode {
    std::unordered_map<char, TrieNode*> children;
    TrieNode* fail;
    std::vector<int> output;

    TrieNode() : fail(nullptr) {}
};

class AhoCorasick {
public:
    AhoCorasick() : root(new TrieNode()) {}
    ~AhoCorasick() { freeNodes(root); }

    void buildTrie(const std::vector<std::string>& patterns);

    void buildFailureLinks();

    std::vector<int> search(const std::string& text) const;

private:
    TrieNode* root;
    void freeNodes(TrieNode* node);
};


#endif //MUNTASHIRAKON_AHOCORASICK_H
