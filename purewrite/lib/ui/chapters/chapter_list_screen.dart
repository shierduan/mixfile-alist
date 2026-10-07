import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/database/app_database.dart';
import '../../providers/providers.dart';

/// 章节列表页：展示某本书的「卷 → 章节」层级
class ChapterListScreen extends ConsumerWidget {
  final int bookId;

  const ChapterListScreen({super.key, required this.bookId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bookAsync = ref.watch(bookProvider(bookId));
    final volumesAsync = ref.watch(volumesStreamProvider(bookId));

    return Scaffold(
      appBar: AppBar(
        title: bookAsync.maybeWhen(
          data: (book) => Text(book?.title ?? '书籍'),
          orElse: () => const Text('书籍'),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.library_books_outlined),
            tooltip: '阅读',
            onPressed: () => context.push('/reader/$bookId'),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showNewChapterDialog(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('新章'),
      ),
      body: volumesAsync.when(
        data: (volumes) {
          if (volumes.isEmpty) {
            return const _EmptyVolumes();
          }
          return ListView.builder(
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: volumes.length,
            itemBuilder: (context, index) {
              return _VolumeCard(volume: volumes[index], bookId: bookId);
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('加载失败: $e')),
      ),
    );
  }

  void _showNewChapterDialog(BuildContext context, WidgetRef ref) {
    final titleController = TextEditingController();
    final formKey = GlobalKey<FormState>();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新建章节'),
        content: Form(
          key: formKey,
          child: TextFormField(
            controller: titleController,
            decoration: const InputDecoration(
              labelText: '章节标题 *',
              prefixIcon: Icon(Icons.article_outlined),
            ),
            autofocus: true,
            validator: (v) =>
                (v == null || v.trim().isEmpty) ? '请输入章节标题' : null,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              if (!formKey.currentState!.validate()) return;
              final repo = ref.read(repositoryProvider);

              // 确保至少有一个卷
              final volumes = await repo.getVolumesByBookList(bookId);
              int volumeId;
              if (volumes.isEmpty) {
                volumeId = await repo.insertVolume(
                  bookId: bookId,
                  title: '第一卷',
                );
              } else {
                volumeId = volumes.first.id;
              }

              final chapterCount =
                  await repo.getChapterCountByVolume(volumeId);
              final chapterId = await repo.insertChapter(
                volumeId: volumeId,
                title: titleController.text.trim(),
                order: chapterCount,
              );

              if (context.mounted) {
                Navigator.pop(context);
                context.push('/editor/$chapterId');
              }
            },
            child: const Text('创建'),
          ),
        ],
      ),
    );
  }
}

class _EmptyVolumes extends StatelessWidget {
  const _EmptyVolumes();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.article_outlined,
            size: 72,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 12),
          Text(
            '还没有章节',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            '点击右下角「新章」开始',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
          ),
        ],
      ),
    );
  }
}

class _VolumeCard extends ConsumerWidget {
  final VolumeEntity volume;
  final int bookId;

  const _VolumeCard({required this.volume, required this.bookId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chaptersAsync = ref.watch(chaptersStreamProvider(volume.id));

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: ExpansionTile(
        initiallyExpanded: true,
        tilePadding: const EdgeInsets.symmetric(horizontal: 16),
        childrenPadding: const EdgeInsets.only(bottom: 8),
        leading: CircleAvatar(
          radius: 16,
          backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: Text(
            '${volume.order + 1}',
            style: TextStyle(
              color: Theme.of(context).colorScheme.onPrimaryContainer,
              fontSize: 13,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        title: Text(
          volume.title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        trailing: IconButton(
          icon: const Icon(Icons.add_circle_outline),
          tooltip: '新建章节',
          onPressed: () => _addChapter(context, ref),
        ),
        children: [
          chaptersAsync.maybeWhen(
            data: (chapters) {
              if (chapters.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(
                    '本卷暂无章节',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.outline,
                    ),
                  ),
                );
              }
              return Column(
                children: chapters.map((chapter) {
                  return _ChapterTile(
                    chapter: chapter,
                    volume: volume,
                    bookId: bookId,
                  );
                }).toList(),
              );
            },
            orElse: () => const Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(
                height: 20,
                width: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _addChapter(BuildContext context, WidgetRef ref) async {
    final titleController = TextEditingController();
    final formKey = GlobalKey<FormState>();

    final confirmed = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新建章节'),
        content: Form(
          key: formKey,
          child: TextFormField(
            controller: titleController,
            decoration: const InputDecoration(labelText: '章节标题 *'),
            autofocus: true,
            validator: (v) =>
                (v == null || v.trim().isEmpty) ? '请输入章节标题' : null,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              if (formKey.currentState!.validate()) {
                Navigator.pop(context, titleController.text.trim());
              }
            },
            child: const Text('创建'),
          ),
        ],
      ),
    );

    if (confirmed != null) {
      final repo = ref.read(repositoryProvider);
      final count = await repo.getChapterCountByVolume(volume.id);
      final chapterId = await repo.insertChapter(
        volumeId: volume.id,
        title: confirmed,
        order: count,
      );
      if (context.mounted) {
        context.push('/editor/$chapterId');
      }
    }
  }
}

class _ChapterTile extends ConsumerWidget {
  final ChapterEntity chapter;
  final VolumeEntity volume;
  final int bookId;

  const _ChapterTile({
    required this.chapter,
    required this.volume,
    required this.bookId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 2),
      leading: Icon(
        Icons.description_outlined,
        size: 20,
        color: Theme.of(context).colorScheme.primary,
      ),
      title: Text(
        chapter.title,
        style: Theme.of(context).textTheme.bodyLarge,
      ),
      subtitle: Text(
        '${chapter.wordCount} 字',
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.outline,
            ),
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.push('/editor/${chapter.id}'),
      onLongPress: () => _showChapterMenu(context, ref),
    );
  }

  void _showChapterMenu(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('编辑'),
              onTap: () {
                Navigator.pop(context);
                context.push('/editor/${chapter.id}');
              },
            ),
            ListTile(
              leading: const Icon(Icons.drive_file_rename_outline),
              title: const Text('重命名'),
              onTap: () {
                Navigator.pop(context);
                _renameChapter(context, ref);
              },
            ),
            ListTile(
              leading: Icon(Icons.delete_outline,
                  color: Theme.of(context).colorScheme.error),
              title: Text('删除',
                  style:
                      TextStyle(color: Theme.of(context).colorScheme.error)),
              onTap: () async {
                Navigator.pop(context);
                await ref.read(repositoryProvider).deleteChapterById(chapter.id);
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _renameChapter(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController(text: chapter.title);
    final confirmed = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('重命名章节'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: '章节标题'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (confirmed != null && confirmed.isNotEmpty) {
      await ref.read(repositoryProvider).updateChapter(
            chapter.copyWith(title: confirmed),
          );
    }
  }
}
