import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/providers.dart';

/// 设置页：统计、关于等
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final booksAsync = ref.watch(booksStreamProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
      ),
      body: ListView(
        children: [
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text(
              '统计',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 12),
            child: booksAsync.maybeWhen(
              data: (books) {
                return Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.menu_book_outlined),
                      title: const Text('书籍总数'),
                      trailing: Text('${books.length}'),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      leading: const Icon(Icons.article_outlined),
                      title: const Text('总字数'),
                      trailing: _TotalWordsWidget(bookIds: books.map((b) => b.id).toList()),
                    ),
                  ],
                );
              },
              orElse: () => const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text(
              '关于',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 12),
            child: Column(
              children: const [
                ListTile(
                  leading: Icon(Icons.info_outline),
                  title: Text('纯写作'),
                  subtitle: Text('现代化跨平台小说创作工具'),
                ),
                Divider(height: 1),
                ListTile(
                  leading: Icon(Icons.code_outlined),
                  title: Text('技术栈'),
                  subtitle: Text('Flutter · Riverpod · Drift · Vosk'),
                ),
                Divider(height: 1),
                ListTile(
                  leading: Icon(Icons.tag_outlined),
                  title: Text('版本'),
                  subtitle: Text('1.0.0'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TotalWordsWidget extends ConsumerStatefulWidget {
  final List<int> bookIds;

  const _TotalWordsWidget({required this.bookIds});

  @override
  ConsumerState<_TotalWordsWidget> createState() => _TotalWordsWidgetState();
}

class _TotalWordsWidgetState extends ConsumerState<_TotalWordsWidget> {
  int _total = 0;

  @override
  void initState() {
    super.initState();
    _calcTotal();
  }

  Future<void> _calcTotal() async {
    final repo = ref.read(repositoryProvider);
    var total = 0;
    for (final id in widget.bookIds) {
      total += await repo.getTotalWordsByBook(id);
    }
    if (mounted) setState(() => _total = total);
  }

  @override
  Widget build(BuildContext context) {
    return Text('$_total 字');
  }
}
