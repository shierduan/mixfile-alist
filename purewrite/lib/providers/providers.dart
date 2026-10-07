import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/database/app_database.dart';
import '../data/repository/writing_repository.dart';

/// 全局数据库实例（单例）
final databaseProvider = Provider<AppDatabase>((ref) {
  return AppDatabase();
});

/// 写作仓库
final repositoryProvider = Provider<WritingRepository>((ref) {
  final db = ref.watch(databaseProvider);
  return WritingRepository(db);
});

/// 全部书籍流（响应式）
final booksStreamProvider = StreamProvider<List<BookEntity>>((ref) {
  final repo = ref.watch(repositoryProvider);
  return repo.getAllBooks();
});

/// 单本书籍
final bookProvider = FutureProvider.family<BookEntity?, int>((ref, id) {
  return ref.watch(repositoryProvider).getBookById(id);
});

/// 某书的全部卷（响应式）
final volumesStreamProvider =
    StreamProvider.family<List<VolumeEntity>, int>((ref, bookId) {
  return ref.watch(repositoryProvider).getVolumesByBook(bookId);
});

/// 某卷的全部章节（响应式）
final chaptersStreamProvider =
    StreamProvider.family<List<ChapterEntity>, int>((ref, volumeId) {
  return ref.watch(repositoryProvider).getChaptersByVolume(volumeId);
});

/// 单章
final chapterProvider = FutureProvider.family<ChapterEntity?, int>((ref, id) {
  return ref.watch(repositoryProvider).getChapterById(id);
});

/// 书籍统计信息
final bookStatsProvider =
    FutureProvider.family<({int chapters, int words}), int>((ref, bookId) async {
  final repo = ref.watch(repositoryProvider);
  final chapters = await repo.getChapterCountByBook(bookId);
  final words = await repo.getTotalWordsByBook(bookId);
  return (chapters: chapters, words: words);
});
