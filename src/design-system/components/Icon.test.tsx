import React from 'react';
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

import { Icon, type IconName } from './Icon';

const iconNames: readonly IconName[] = [
  'home',
  'people',
  'settings',
  'shield',
  'check',
  'warning',
  'info',
  'clock',
  'activity',
  'message',
  'pause',
  'play',
  'bell',
  'lock',
  'chevron',
  'search',
  'person',
];

it('renders every named icon as a fixed-size decorative vector', async () => {
  const rendered = await render(
    <>
      {iconNames.map(name => (
        <Icon key={name} color="#315B91" name={name} size={28} />
      ))}
    </>,
  );

  const vectors = rendered.container.queryAll(node =>
    node.type.startsWith('RNSVGSvgView'),
  );
  expect(vectors).toHaveLength(iconNames.length);
  for (const vector of vectors) {
    expect(vector.props.accessible).toBe(false);
    expect(vector.props.focusable).toBe(false);
    expect(StyleSheet.flatten(vector.props.style).height).toBe(28);
    expect(StyleSheet.flatten(vector.props.style).width).toBe(28);
  }
  const filledPath = rendered.container
    .queryAll(node => node.type === 'RNSVGPath')
    .find(path => path.props.fill !== null);
  expect(filledPath?.props.fill).toEqual(
    expect.objectContaining({ type: 0, payload: expect.anything() }),
  );
  expect(filledPath?.props.fill).not.toBe('#315B91');

  const decorativeContainers = rendered.container
    .queryAll(node => node.type === 'View')
    .filter(view => view.props.accessibilityElementsHidden === true);
  expect(decorativeContainers).toHaveLength(iconNames.length);
  expect(
    decorativeContainers.every(
      view => view.props.importantForAccessibility === 'no-hide-descendants',
    ),
  ).toBe(true);
});

it('mirrors the vector chevron without changing its public dimensions', async () => {
  const rendered = await render(
    <Icon color="#171824" mirrored name="chevron" size={22} />,
  );

  const vectors = rendered.container.queryAll(node =>
    node.type.startsWith('RNSVGSvgView'),
  );
  const paths = rendered.container.queryAll(node => node.type === 'RNSVGPath');
  expect(StyleSheet.flatten(vectors[0]!.props.style).width).toBe(22);
  expect(paths[0]!.props.matrix).toEqual([-1, 0, 0, 1, 24, 0]);
});
