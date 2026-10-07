import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/database/app_database.dart';
import '../../providers/providers.dart';
import '../../util/chinese_punctuation.dart';
import '../../util/sensitive_word.dart';
import '../../util/word_counter.dart';

/// 章节编辑器：纯文本编辑，含标点转换、撤销重做、自动保存、敏感词检测
class EditorScreen extends ConsumerStatefulWidget {
  final int chapterId;

  const EditorScreen({super.key, required this.chapterId});

  @override
  ConsumerState<EditorScreen> createState() => _EditorScreenState();
}

class _EditorScreenState extends ConsumerState<EditorScreen> {
  late TextEditingController _controller;
  final _undoStack = <String>[];
  final _redoStack = <String>[];
  bool _isUndoing = false;

  Timer? _autoSaveTimer;
  bool _isDirty = false;
  ChapterEntity? _chapter;

  final bool _showWordCount = true;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _loadChapter();
  }

  @override
  void dispose() {
    _autoSaveTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _loadChapter() async {
    final repo = ref.read(repositoryProvider);
    final chapter = await repo.getChapterById(widget.chapterId);
    if (chapter == null) return;

    setState(() {
      _chapter = chapter;
      _controller.text = chapter.content;
    });
  }

  void _onTextChanged(String text) {
    if (!_isUndoing) {
      _undoStack.add(_controller.text);
      if (_undoStack.length > 100) _undoStack.removeAt(0);
      _redoStack.clear();
    }
    _isDirty = true;
    _scheduleAutoSave();
    setState(() {});
  }

  void _scheduleAutoSave() {
    _autoSaveTimer?.cancel();
    _autoSaveTimer = Timer(const Duration(seconds: 2), () {
      _saveChapter();
    });
  }

  Future<void> _saveChapter() async {
    if (_chapter == null) return;
    final repo = ref.read(repositoryProvider);
    await repo.updateChapter(_chapter!.copyWith(
      content: _controller.text,
      wordCount: WordCounter.count(_controller.text),
    ));
    if (mounted) {
      setState(() => _isDirty = false);
    }
  }

  void _undo() {
    if (_undoStack.isEmpty) return;
    _isUndoing = true;
    _redoStack.add(_controller.text);
    final text = _undoStack.removeLast();
    _controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _isUndoing = false;
    _isDirty = true;
    _scheduleAutoSave();
    setState(() {});
  }

  void _redo() {
    if (_redoStack.isEmpty) return;
    _isUndoing = true;
    _undoStack.add(_controller.text);
    final text = _redoStack.removeLast();
    _controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _isUndoing = false;
    _isDirty = true;
    _scheduleAutoSave();
    setState(() {});
  }

  void _convertPunctuation() {
    final converted = ChinesePunctuationConverter.convert(_controller.text);
    if (converted != _controller.text) {
      _undoStack.add(_controller.text);
      if (_undoStack.length > 100) _undoStack.removeAt(0);
      _redoStack.clear();
      _controller.text = converted;
      _isDirty = true;
      _scheduleAutoSave();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('已转换中文标点')),
      );
    }
  }

  void _showFindReplace() {
    final findController = TextEditingController();
    final replaceController = TextEditingController();
    var matchCount = 0;

    showDialog(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: const Text('查找与替换'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: findController,
                  decoration: const InputDecoration(
                    labelText: '查找',
                    prefixIcon: Icon(Icons.search),
                  ),
                  autofocus: true,
                  onChanged: (v) {
                    setDialogState(() {
                      matchCount = v.isEmpty
                          ? 0
                          : _countOccurrences(_controller.text, v);
                    });
                  },
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: replaceController,
                  decoration: const InputDecoration(
                    labelText: '替换为',
                    prefixIcon: Icon(Icons.find_replace),
                  ),
                ),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    findController.text.isEmpty
                        ? ''
                        : '找到 $matchCount 处匹配',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.outline,
                        ),
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () {
                  if (findController.text.isEmpty) return;
                  final newText = _controller.text
                      .replaceAll(findController.text, replaceController.text);
                  _undoStack.add(_controller.text);
                  if (_undoStack.length > 100) _undoStack.removeAt(0);
                  _redoStack.clear();
                  _controller.text = newText;
                  _isDirty = true;
                  _scheduleAutoSave();
                  Navigator.pop(context);
                },
                child: const Text('全部替换'),
              ),
            ],
          );
        },
      ),
    );
  }

  int _countOccurrences(String text, String pattern) {
    if (pattern.isEmpty) return 0;
    var count = 0;
    var start = 0;
    while (true) {
      final index = text.indexOf(pattern, start);
      if (index == -1) break;
      count++;
      start = index + pattern.length;
    }
    return count;
  }

  void _checkSensitive() {
    final results = SensitiveWordManager.check(_controller.text);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('敏感词检测'),
        content: results.isEmpty
            ? const Text('未检测到敏感词')
            : SizedBox(
                width: double.maxFinite,
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: results.length,
                  itemBuilder: (context, index) {
                    final r = results[index];
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.warning_amber,
                          color: Colors.orange),
                      title: Text(r.word),
                      subtitle: Text('位置: ${r.start + 1}'),
                    );
                  },
                ),
              ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('关闭'),
          ),
          if (results.isNotEmpty)
            FilledButton(
              onPressed: () {
                _controller.text = SensitiveWordManager.mask(_controller.text);
                _isDirty = true;
                _scheduleAutoSave();
                Navigator.pop(context);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('已用 * 替换敏感词')),
                );
              },
              child: const Text('一键替换'),
            ),
        ],
      ),
    );
  }

  void _openPreview() {
    _saveChapter();
    context.push('/preview/${widget.chapterId}');
  }

  @override
  Widget build(BuildContext context) {
    final wordCount = WordCounter.count(_controller.text);

    return Scaffold(
      appBar: AppBar(
        title: Text(_chapter?.title ?? '编辑器'),
        actions: [
          IconButton(
            icon: const Icon(Icons.undo),
            tooltip: '撤销',
            onPressed: _undoStack.isEmpty ? null : _undo,
          ),
          IconButton(
            icon: const Icon(Icons.redo),
            tooltip: '重做',
            onPressed: _redoStack.isEmpty ? null : _redo,
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              switch (value) {
                case 'punctuation':
                  _convertPunctuation();
                  break;
                case 'find':
                  _showFindReplace();
                  break;
                case 'sensitive':
                  _checkSensitive();
                  break;
                case 'preview':
                  _openPreview();
                  break;
                case 'save':
                  _saveChapter();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('已保存')),
                  );
                  break;
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'punctuation',
                child: ListTile(
                  leading: Icon(Icons.text_fields),
                  title: Text('转换中文标点'),
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const PopupMenuItem(
                value: 'find',
                child: ListTile(
                  leading: Icon(Icons.find_replace),
                  title: Text('查找与替换'),
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const PopupMenuItem(
                value: 'sensitive',
                child: ListTile(
                  leading: Icon(Icons.shield_outlined),
                  title: Text('敏感词检测'),
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const PopupMenuItem(
                value: 'preview',
                child: ListTile(
                  leading: Icon(Icons.visibility_outlined),
                  title: Text('预览'),
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const PopupMenuItem(
                value: 'save',
                child: ListTile(
                  leading: Icon(Icons.save_outlined),
                  title: Text('保存'),
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: TextField(
              controller: _controller,
              onChanged: _onTextChanged,
              maxLines: null,
              expands: true,
              textAlignVertical: TextAlignVertical.top,
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    height: 1.8,
                    fontSize: 16,
                  ),
              decoration: const InputDecoration(
                hintText: '开始写作...',
                border: InputBorder.none,
                contentPadding: EdgeInsets.all(20),
              ),
            ),
          ),
          if (_showWordCount)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                border: Border(
                  top: BorderSide(
                    color: Theme.of(context).colorScheme.outlineVariant,
                  ),
                ),
              ),
              child: Row(
                children: [
                  Text(
                    '$wordCount 字',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                  const Spacer(),
                  if (_isDirty)
                    Text(
                      '未保存',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Theme.of(context).colorScheme.error,
                          ),
                    )
                  else
                    Icon(
                      Icons.check,
                      size: 16,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
