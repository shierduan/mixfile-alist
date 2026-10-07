import 'package:drift/drift.dart';

import '../database/app_database.dart';

/// 写作仓库：封装书籍 / 卷 / 章节的所有数据操作
/// 对应 Android 端 WritingRepository，提供 Flow (Stream) 响应式查询
class WritingRepository {
  final AppDatabase _db;

  WritingRepository(this._db);

  BookDao get _bookDao => _db.bookDao;
  VolumeDao get _volumeDao => _db.volumeDao;
  ChapterDao get _chapterDao => _db.chapterDao;

  // ==================== Book ====================

  Stream<List<BookEntity>> getAllBooks() => _bookDao.getAllBooks();

  Future<BookEntity?> getBookById(int id) => _bookDao.getBookById(id);

  Future<int> insertBook({
    required String title,
    String author = '',
    String description = '',
    int coverColor = 0xFF1A73E8,
  }) {
    final now = DateTime.now().millisecondsSinceEpoch;
    return _bookDao.insertBook(BooksCompanion(
      title: Value(title),
      author: Value(author),
      description: Value(description),
      coverColor: Value(coverColor),
      createdAt: Value(now),
      updatedAt: Value(now),
      lastOpenedAt: Value(now),
    ));
  }

  Future<bool> updateBook(BookEntity book) => _bookDao.updateBook(book.copyWith(
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));

  Future<int> deleteBook(BookEntity book) => _bookDao.deleteBook(book);

  Future<int> deleteBookById(int id) => _bookDao.deleteBookById(id);

  Future<List<BookEntity>> getAllBooksList() => _bookDao.getAllBooksList();

  Future<void> clearAll() async {
    await _chapterDao.deleteAllChapters();
    await _volumeDao.deleteAllVolumes();
    await _bookDao.deleteAllBooks();
  }

  Future<int> updateLastOpened(int id) =>
      _bookDao.updateLastOpened(id, DateTime.now().millisecondsSinceEpoch);

  // ==================== 回收站（软删除） ====================

  Future<int> softDeleteBook(int id) => _bookDao.softDeleteBook(id);
  Future<int> restoreBook(int id) => _bookDao.restoreBook(id);
  Future<List<BookEntity>> getDeletedBooks() => _bookDao.getDeletedBooks();

  Future<int> softDeleteVolume(int id) => _volumeDao.softDeleteVolume(id);
  Future<int> restoreVolume(int id) => _volumeDao.restoreVolume(id);
  Future<List<VolumeEntity>> getDeletedVolumes() =>
      _volumeDao.getDeletedVolumes();

  Future<int> softDeleteChapter(int id) => _chapterDao.softDeleteChapter(id);
  Future<int> restoreChapter(int id) => _chapterDao.restoreChapter(id);
  Future<List<ChapterEntity>> getDeletedChapters() =>
      _chapterDao.getDeletedChapters();

  Future<int> permanentDeleteBook(int id) => _bookDao.deleteBookById(id);

  // ==================== Volume ====================

  Stream<List<VolumeEntity>> getVolumesByBook(int bookId) =>
      _volumeDao.getVolumesByBook(bookId);

  Future<List<VolumeEntity>> getVolumesByBookList(int bookId) =>
      _volumeDao.getVolumesByBookList(bookId);

  Future<VolumeEntity?> getVolumeById(int id) => _volumeDao.getVolumeById(id);

  Future<int> insertVolume({
    required int bookId,
    required String title,
    int order = 0,
  }) {
    final now = DateTime.now().millisecondsSinceEpoch;
    return _volumeDao.insertVolume(VolumesCompanion(
      bookId: Value(bookId),
      title: Value(title),
      order: Value(order),
      createdAt: Value(now),
      updatedAt: Value(now),
    ));
  }

  Future<bool> updateVolume(VolumeEntity volume) =>
      _volumeDao.updateVolume(volume.copyWith(
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));

  Future<int> deleteVolume(VolumeEntity volume) =>
      _volumeDao.deleteVolume(volume);

  Future<int> deleteVolumeById(int id) => _volumeDao.deleteVolumeById(id);

  Future<int> getVolumeCount(int bookId) => _volumeDao.getVolumeCount(bookId);

  Future<List<VolumeEntity>> getAllVolumesList() =>
      _volumeDao.getAllVolumesList();

  // ==================== Chapter ====================

  Stream<List<ChapterEntity>> getChaptersByVolume(int volumeId) =>
      _chapterDao.getChaptersByVolume(volumeId);

  Future<List<ChapterEntity>> getChaptersByVolumeList(int volumeId) =>
      _chapterDao.getChaptersByVolumeList(volumeId);

  Future<ChapterEntity?> getChapterById(int id) =>
      _chapterDao.getChapterById(id);

  Future<int> insertChapter({
    required int volumeId,
    required String title,
    String content = '',
    int order = 0,
    int wordCount = 0,
  }) {
    final now = DateTime.now().millisecondsSinceEpoch;
    return _chapterDao.insertChapter(ChaptersCompanion(
      volumeId: Value(volumeId),
      title: Value(title),
      content: Value(content),
      order: Value(order),
      wordCount: Value(wordCount),
      createdAt: Value(now),
      updatedAt: Value(now),
    ));
  }

  Future<bool> updateChapter(ChapterEntity chapter) =>
      _chapterDao.updateChapter(chapter.copyWith(
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));

  Future<int> deleteChapter(ChapterEntity chapter) =>
      _chapterDao.deleteChapter(chapter);

  Future<int> deleteChapterById(int id) => _chapterDao.deleteChapterById(id);

  Future<int> getChapterCountByVolume(int volumeId) =>
      _chapterDao.getChapterCountByVolume(volumeId);

  Future<List<ChapterEntity>> getChaptersByBook(int bookId) =>
      _chapterDao.getChaptersByBook(bookId);

  Future<int> getChapterCountByBook(int bookId) =>
      _chapterDao.getChapterCountByBook(bookId);

  Future<int> getTotalWordsByBook(int bookId) =>
      _chapterDao.getTotalWordsByBook(bookId);

  Future<List<ChapterEntity>> getAllChaptersList() =>
      _chapterDao.getAllChaptersList();
}
