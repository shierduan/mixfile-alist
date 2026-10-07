import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

part 'app_database.g.dart';

// ==================== 表定义 ====================

@DataClassName('BookEntity')
class Books extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text()();
  TextColumn get author => text().withDefault(const Constant(''))();
  TextColumn get description => text().withDefault(const Constant(''))();
  IntColumn get coverColor =>
      integer().withDefault(Constant(0xFF1A73E8.toInt()))();
  IntColumn get createdAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get updatedAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get lastOpenedAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get deletedAt => integer().withDefault(const Constant(0))();
}

@DataClassName('VolumeEntity')
class Volumes extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get bookId => integer().references(Books, #id,
      onDelete: KeyAction.cascade)();
  TextColumn get title => text()();
  IntColumn get order => integer().withDefault(const Constant(0))();
  IntColumn get createdAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get updatedAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get deletedAt => integer().withDefault(const Constant(0))();
}

@DataClassName('ChapterEntity')
class Chapters extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get volumeId => integer().references(Volumes, #id,
      onDelete: KeyAction.cascade)();
  TextColumn get title => text()();
  TextColumn get content => text().withDefault(const Constant(''))();
  IntColumn get order => integer().withDefault(const Constant(0))();
  IntColumn get wordCount => integer().withDefault(const Constant(0))();
  IntColumn get createdAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get updatedAt =>
      integer().withDefault(Constant(DateTime.now().millisecondsSinceEpoch))();
  IntColumn get deletedAt => integer().withDefault(const Constant(0))();
}

// ==================== 数据库 ====================

@DriftDatabase(
  tables: [Books, Volumes, Chapters],
  daos: [BookDao, VolumeDao, ChapterDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (Migrator m) async {
          await m.createAll();
        },
      );
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationDocumentsDirectory();
    final file = File(p.join(dbFolder.path, 'purewriter.db'));
    return NativeDatabase.createInBackground(file);
  });
}

// ==================== BookDao ====================

@DriftAccessor(tables: [Books])
class BookDao extends DatabaseAccessor<AppDatabase> with _$BookDaoMixin {
  BookDao(super.db);

  Stream<List<BookEntity>> getAllBooks() {
    return (select(books)
          ..where((b) => b.deletedAt.equals(0))
          ..orderBy([
            (b) => OrderingTerm.desc(b.lastOpenedAt),
            (b) => OrderingTerm.desc(b.updatedAt),
          ]))
        .watch();
  }

  Future<BookEntity?> getBookById(int id) =>
      (select(books)..where((b) => b.id.equals(id))).getSingleOrNull();

  Future<List<BookEntity>> getAllBooksList() => (select(books)
        ..where((b) => b.deletedAt.equals(0))
        ..orderBy([
          (b) => OrderingTerm.desc(b.lastOpenedAt),
          (b) => OrderingTerm.desc(b.updatedAt),
        ]))
      .get();

  Future<List<BookEntity>> getDeletedBooks() => (select(books)
        ..where((b) => b.deletedAt.isBiggerThanValue(0))
        ..orderBy([(b) => OrderingTerm.desc(b.deletedAt)]))
      .get();

  Future<int> insertBook(BooksCompanion book) => into(books).insert(book);

  Future<bool> updateBook(BookEntity book) => update(books).replace(book);

  Future<int> deleteBook(BookEntity book) => delete(books).delete(book);

  Future<int> deleteBookById(int id) =>
      (delete(books)..where((b) => b.id.equals(id))).go();

  Future<int> softDeleteBook(int id) => (update(books)
        ..where((b) => b.id.equals(id)))
      .write(BooksCompanion(deletedAt: Value(DateTime.now().millisecondsSinceEpoch)));

  Future<int> restoreBook(int id) => (update(books)
        ..where((b) => b.id.equals(id)))
      .write(const BooksCompanion(deletedAt: Value(0)));

  Future<int> updateLastOpened(int id, int time) => (update(books)
        ..where((b) => b.id.equals(id)))
      .write(BooksCompanion(lastOpenedAt: Value(time)));

  Future<int> deleteAllBooks() => delete(books).go();
}

// ==================== VolumeDao ====================

@DriftAccessor(tables: [Volumes])
class VolumeDao extends DatabaseAccessor<AppDatabase> with _$VolumeDaoMixin {
  VolumeDao(super.db);

  Stream<List<VolumeEntity>> getVolumesByBook(int bookId) {
    return (select(volumes)
          ..where((v) => v.bookId.equals(bookId) & v.deletedAt.equals(0))
          ..orderBy([
            (v) => OrderingTerm.asc(v.order),
            (v) => OrderingTerm.asc(v.createdAt),
          ]))
        .watch();
  }

  Future<List<VolumeEntity>> getVolumesByBookList(int bookId) =>
      (select(volumes)
            ..where((v) => v.bookId.equals(bookId) & v.deletedAt.equals(0))
            ..orderBy([
              (v) => OrderingTerm.asc(v.order),
              (v) => OrderingTerm.asc(v.createdAt),
            ]))
          .get();

  Future<VolumeEntity?> getVolumeById(int id) =>
      (select(volumes)..where((v) => v.id.equals(id))).getSingleOrNull();

  Future<List<VolumeEntity>> getAllVolumesList() => (select(volumes)
        ..where((v) => v.deletedAt.equals(0))
        ..orderBy([
          (v) => OrderingTerm.asc(v.order),
          (v) => OrderingTerm.asc(v.createdAt),
        ]))
      .get();

  Future<List<VolumeEntity>> getDeletedVolumes() => (select(volumes)
        ..where((v) => v.deletedAt.isBiggerThanValue(0))
        ..orderBy([(v) => OrderingTerm.desc(v.deletedAt)]))
      .get();

  Future<int> insertVolume(VolumesCompanion volume) =>
      into(volumes).insert(volume);

  Future<bool> updateVolume(VolumeEntity volume) =>
      update(volumes).replace(volume);

  Future<int> deleteVolume(VolumeEntity volume) => delete(volumes).delete(volume);

  Future<int> deleteVolumeById(int id) =>
      (delete(volumes)..where((v) => v.id.equals(id))).go();

  Future<int> softDeleteVolume(int id) => (update(volumes)
        ..where((v) => v.id.equals(id)))
      .write(VolumesCompanion(
          deletedAt: Value(DateTime.now().millisecondsSinceEpoch)));

  Future<int> restoreVolume(int id) => (update(volumes)
        ..where((v) => v.id.equals(id)))
      .write(const VolumesCompanion(deletedAt: Value(0)));

  Future<int> getVolumeCount(int bookId) async {
    final result = await (selectOnly(volumes)
          ..where(volumes.bookId.equals(bookId) & volumes.deletedAt.equals(0))
          ..addColumns([volumes.id.count()]))
        .getSingle();
    return result.read<int>(volumes.id.count()) ?? 0;
  }

  Future<int> deleteAllVolumes() => delete(volumes).go();
}

// ==================== ChapterDao ====================

@DriftAccessor(tables: [Chapters, Volumes])
class ChapterDao extends DatabaseAccessor<AppDatabase> with _$ChapterDaoMixin {
  ChapterDao(super.db);

  Stream<List<ChapterEntity>> getChaptersByVolume(int volumeId) {
    return (select(chapters)
          ..where((c) => c.volumeId.equals(volumeId) & c.deletedAt.equals(0))
          ..orderBy([
            (c) => OrderingTerm.asc(c.order),
            (c) => OrderingTerm.asc(c.createdAt),
          ]))
        .watch();
  }

  Future<List<ChapterEntity>> getChaptersByVolumeList(int volumeId) =>
      (select(chapters)
            ..where((c) => c.volumeId.equals(volumeId) & c.deletedAt.equals(0))
            ..orderBy([
              (c) => OrderingTerm.asc(c.order),
              (c) => OrderingTerm.asc(c.createdAt),
            ]))
          .get();

  Future<ChapterEntity?> getChapterById(int id) =>
      (select(chapters)..where((c) => c.id.equals(id))).getSingleOrNull();

  Future<List<ChapterEntity>> getAllChaptersList() => (select(chapters)
        ..where((c) => c.deletedAt.equals(0))
        ..orderBy([
          (c) => OrderingTerm.asc(c.order),
          (c) => OrderingTerm.asc(c.createdAt),
        ]))
      .get();

  Future<List<ChapterEntity>> getDeletedChapters() => (select(chapters)
        ..where((c) => c.deletedAt.isBiggerThanValue(0))
        ..orderBy([(c) => OrderingTerm.desc(c.deletedAt)]))
      .get();

  Future<int> insertChapter(ChaptersCompanion chapter) =>
      into(chapters).insert(chapter);

  Future<bool> updateChapter(ChapterEntity chapter) =>
      update(chapters).replace(chapter);

  Future<int> deleteChapter(ChapterEntity chapter) =>
      delete(chapters).delete(chapter);

  Future<int> deleteChapterById(int id) =>
      (delete(chapters)..where((c) => c.id.equals(id))).go();

  Future<int> softDeleteChapter(int id) => (update(chapters)
        ..where((c) => c.id.equals(id)))
      .write(ChaptersCompanion(
          deletedAt: Value(DateTime.now().millisecondsSinceEpoch)));

  Future<int> restoreChapter(int id) => (update(chapters)
        ..where((c) => c.id.equals(id)))
      .write(const ChaptersCompanion(deletedAt: Value(0)));

  Future<int> getChapterCountByVolume(int volumeId) async {
    final result = await (selectOnly(chapters)
          ..where(chapters.volumeId.equals(volumeId) & chapters.deletedAt.equals(0))
          ..addColumns([chapters.id.count()]))
        .getSingle();
    return result.read<int>(chapters.id.count()) ?? 0;
  }

  /// 获取书籍全部章节（通过 JOIN 卷表），按卷顺序+章节顺序排列
  Future<List<ChapterEntity>> getChaptersByBook(int bookId) async {
    final query = select(chapters).join([
      innerJoin(volumes, volumes.id.equalsExp(chapters.volumeId)),
    ])
      ..where(volumes.bookId.equals(bookId) & chapters.deletedAt.equals(0))
      ..orderBy([
        OrderingTerm.asc(volumes.order),
        OrderingTerm.asc(volumes.createdAt),
        OrderingTerm.asc(chapters.order),
        OrderingTerm.asc(chapters.createdAt),
      ]);
    return query.map((row) => row.readTable(chapters)).get();
  }

  Future<int> getChapterCountByBook(int bookId) async {
    final result = await (selectOnly(chapters).join([
      innerJoin(volumes, volumes.id.equalsExp(chapters.volumeId)),
    ])
          ..where(volumes.bookId.equals(bookId) & chapters.deletedAt.equals(0))
          ..addColumns([chapters.id.count()]))
        .getSingle();
    return result.read<int>(chapters.id.count()) ?? 0;
  }

  Future<int> getTotalWordsByBook(int bookId) async {
    final result = await (selectOnly(chapters).join([
      innerJoin(volumes, volumes.id.equalsExp(chapters.volumeId)),
    ])
          ..where(volumes.bookId.equals(bookId) & chapters.deletedAt.equals(0))
          ..addColumns([chapters.wordCount.sum()]))
        .getSingle();
    return result.read<int>(chapters.wordCount.sum()) ?? 0;
  }

  Future<int> deleteAllChapters() => delete(chapters).go();
}
