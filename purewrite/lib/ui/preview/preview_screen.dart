import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/database/app_database.dart';
import '../../providers/providers.dart';

/// Markdown 预览页
class PreviewScreen extends ConsumerWidget {
  final int chapterId;

  const PreviewScreen({super.key, required this.chapterId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chapterAsync = ref.watch(chapterProvider(chapterId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('预览'),
      ),
      body: chapterAsync.maybeWhen(
        data: (chapter) {
          if (chapter == null) {
            return const Center(child: Text('章节不存在'));
          }
          return Markdown(
            data: _toMarkdown(chapter),
            padding: const EdgeInsets.all(20),
            styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context)).copyWith(
              p: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    height: 1.8,
                    fontSize: 16,
                  ),
              h1: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
              h2: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
          );
        },
        orElse: () => const Center(child: CircularProgressIndicator()),
      ),
    );
  }

  String _toMarkdown(ChapterEntity chapter) {
    final buffer = StringBuffer();
    buffer.writeln('# ${chapter.title}');
    buffer.writeln();
    buffer.writeln(chapter.content);
    return buffer.toString();
  }
}
