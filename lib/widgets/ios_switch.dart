import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// iOS Cupertino styled switch with theme accent color and haptic feedback.
class IosSwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool>? onChanged;

  const IosSwitch({
    super.key,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return CupertinoSwitch(
      value: value,
      activeTrackColor: theme.colorScheme.primary,
      onChanged: onChanged == null
          ? null
          : (val) {
              HapticFeedback.lightImpact();
              onChanged!(val);
            },
    );
  }
}

