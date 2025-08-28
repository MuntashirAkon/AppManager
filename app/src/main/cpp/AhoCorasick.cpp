// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <string>
#include <vector>
#include <queue>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <map>
#include <stack>

#include "AhoCorasick.h"

void AhoCorasick::buildTrie(const std::vector<std::string> &patterns) {
    for (int i = 0; i < (int) patterns.size(); ++i) {
        const std::string &pat = patterns[i];
        TrieNode *node = root;
        for (char c: pat) {
            if (!node->children.count(c)) node->children[c] = new TrieNode();
            node = node->children[c];
        }
        node->output.push_back(i);
    }
}

void AhoCorasick::buildFailureLinks() {
    std::queue<TrieNode *> q;
    root->fail = root;
    for (auto &pair: root->children) {
        pair.second->fail = root;
        q.push(pair.second);
    }
    while (!q.empty()) {
        TrieNode *current = q.front();
        q.pop();
        for (auto &pair: current->children) {
            char c = pair.first;
            TrieNode *child = pair.second;
            TrieNode *f = current->fail;
            while (f != root && !f->children.count(c)) {
                f = f->fail;
            }
            if (f->children.count(c) && f->children[c] != child) {
                child->fail = f->children[c];
            } else {
                child->fail = root;
            }
            child->output.insert(child->output.end(),
                                 child->fail->output.begin(),
                                 child->fail->output.end());
            q.push(child);
        }
    }
}

std::vector<int> AhoCorasick::search(const std::string &text) const {
    std::vector<int> matches;
    TrieNode *node = root;
    for (char c: text) {
        while (node != root && !node->children.count(c)) {
            node = node->fail;
        }
        if (node->children.count(c)) {
            node = node->children.at(c);
        }
        matches.insert(matches.end(), node->output.begin(), node->output.end());
    }
    return matches;
}

void AhoCorasick::freeNodes(TrieNode* node) {
    if (!node) return;

    std::stack<TrieNode*> stack;
    stack.push(node);

    while (!stack.empty()) {
        TrieNode* tmpNode = stack.top();
        stack.pop();

        // Push children onto stack before deleting the current tmpNode
        for (auto& pair : tmpNode->children) {
            stack.push(pair.second);
        }
        delete tmpNode;
    }
}