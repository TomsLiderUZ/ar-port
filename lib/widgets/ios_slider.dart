import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// iOS-styled Cupertino slider with dynamic color theme.
class IosSlider extends StatelessWidget {
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final ValueChanged<double> onChanged;

  const IosSlider({
    super.key,
    required this.value,
    this.min = 0.0,
    this.max = 1.0,
    this.divisions,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return CupertinoSlider(
      value: value.clamp(min, max),
      min: min,
      max: max,
      divisions: divisions,
      activeColor: theme.colorScheme.primary,
      thumbColor: Colors.white,
      onChanged: (val) {
        if (divisions != null) {
          HapticFeedback.selectionClick();
        }
        onChanged(val);
      },
    );
  }
}

