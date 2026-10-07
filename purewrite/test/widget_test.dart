import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:purewrite/main.dart';

void main() {
  testWidgets('App launches and shows bookshelf app bar',
      (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: PureWriteApp()));
    // Pump a few frames to let the widget tree build
    await tester.pump(const Duration(milliseconds: 100));

    // AppBar title should be visible regardless of async data loading
    expect(find.text('纯写作'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsOneWidget);
  });
}
