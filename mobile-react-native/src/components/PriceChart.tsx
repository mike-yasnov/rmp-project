import React, { useMemo } from 'react';
import { View, StyleSheet, LayoutChangeEvent, useWindowDimensions } from 'react-native';
import Svg, { Line, Path, Rect } from 'react-native-svg';
import { Candle, ChartType } from '../types';
import { useTheme } from '../ThemeProvider';

interface Props {
  candles: Candle[];
  type: ChartType;
  height?: number;
}

export function PriceChart({ candles, type, height = 220 }: Props) {
  const { palette } = useTheme();
  const { width: winWidth } = useWindowDimensions();
  const [width, setWidth] = React.useState<number>(winWidth - 32);

  const onLayout = (e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    if (w > 0) setWidth(w);
  };

  const data = useMemo(() => {
    if (candles.length === 0) return null;
    const minP = Math.min(...candles.map((c) => c.low));
    const maxP = Math.max(...candles.map((c) => c.high));
    const rng = Math.max(1e-9, maxP - minP);
    const padX = 8;
    const padY = 12;
    const w = Math.max(50, width - 2 * padX);
    const h = height - 2 * padY;
    const slot = w / candles.length;
    return { minP, maxP, rng, padX, padY, w, h, slot };
  }, [candles, width, height]);

  return (
    <View
      style={[styles.wrap, { height, backgroundColor: palette.surface }]}
      onLayout={onLayout}
    >
      <Svg width="100%" height={height}>
        {/* grid */}
        {[0, 1, 2, 3, 4].map((i) => {
          if (!data) return null;
          const y = data.padY + (data.h * i) / 4;
          return (
            <Line
              key={`g-${i}`}
              x1={data.padX}
              x2={data.padX + data.w}
              y1={y}
              y2={y}
              stroke={palette.borderSubtle}
              strokeWidth={1}
            />
          );
        })}
        {data &&
          (type === 'line' ? (
            <Path
              d={candles
                .map((c, i) => {
                  const x = data.padX + data.slot * (i + 0.5);
                  const y = data.padY + data.h * (1 - (c.close - data.minP) / data.rng);
                  return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
                })
                .join(' ')}
              stroke={palette.accentBrand}
              strokeWidth={2.5}
              fill="none"
            />
          ) : (
            candles.map((c, i) => {
              const cx = data.padX + data.slot * (i + 0.5);
              const isUp = c.close >= c.open;
              const color = isUp ? palette.accentUp : palette.accentDown;
              const yHigh = data.padY + data.h * (1 - (c.high - data.minP) / data.rng);
              const yLow = data.padY + data.h * (1 - (c.low - data.minP) / data.rng);
              const yOpen = data.padY + data.h * (1 - (c.open - data.minP) / data.rng);
              const yClose = data.padY + data.h * (1 - (c.close - data.minP) / data.rng);
              const top = Math.min(yOpen, yClose);
              const bodyH = Math.max(1, Math.abs(yClose - yOpen));
              const bodyW = Math.max(2, data.slot * 0.6);
              return (
                <React.Fragment key={c.timestamp + i}>
                  <Line x1={cx} x2={cx} y1={yHigh} y2={yLow} stroke={color} strokeWidth={1.2} />
                  <Rect x={cx - bodyW / 2} y={top} width={bodyW} height={bodyH} fill={color} />
                </React.Fragment>
              );
            })
          ))}
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { borderRadius: 12, overflow: 'hidden' },
});
