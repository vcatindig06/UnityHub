import 'package:flutter_test/flutter_test.dart';
import 'package:worship/main.dart';

void main() {
  testWidgets('welcome screen displays UnityHub', (tester) async {
    await tester.pumpWidget(const UnityHubApp());

    expect(find.text('UnityHub'), findsOneWidget);
    expect(find.text('Worship in unity.'), findsOneWidget);
    expect(find.text('Song library'), findsOneWidget);
    expect(find.text('Service planning'), findsOneWidget);
  });
}
