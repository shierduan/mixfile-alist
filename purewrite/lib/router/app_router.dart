import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../ui/bookshelf/bookshelf_screen.dart';
import '../ui/chapters/chapter_list_screen.dart';
import '../ui/editor/editor_screen.dart';
import '../ui/preview/preview_screen.dart';
import '../ui/settings/settings_screen.dart';

/// 全局路由配置（GoRouter 声明式路由）
final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const BookshelfScreen(),
      ),
      GoRoute(
        path: '/book/:bookId',
        builder: (context, state) {
          final bookId = int.parse(state.pathParameters['bookId']!);
          return ChapterListScreen(bookId: bookId);
        },
      ),
      GoRoute(
        path: '/editor/:chapterId',
        builder: (context, state) {
          final chapterId = int.parse(state.pathParameters['chapterId']!);
          return EditorScreen(chapterId: chapterId);
        },
      ),
      GoRoute(
        path: '/preview/:chapterId',
        builder: (context, state) {
          final chapterId = int.parse(state.pathParameters['chapterId']!);
          return PreviewScreen(chapterId: chapterId);
        },
      ),
      GoRoute(
        path: '/reader/:bookId',
        builder: (context, state) {
          final bookId = int.parse(state.pathParameters['bookId']!);
          return _ReaderPlaceholder(bookId: bookId);
        },
      ),
      GoRoute(
        path: '/settings',
        builder: (context, state) => const SettingsScreen(),
      ),
    ],
  );
});

class _ReaderPlaceholder extends StatelessWidget {
  final int bookId;
  const _ReaderPlaceholder({required this.bookId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('阅读')),
      body: const Center(child: Text('阅读器开发中...')),
    );
  }
}
