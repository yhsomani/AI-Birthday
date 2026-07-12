import React from 'react';
import { cleanup, render, screen } from '@testing-library/react-native';

import { DeviceE2EFixtureApp } from './DeviceE2EFixtureApp';

jest.mock('../../src/app/FixturePreviewApp', () => {
  const TestReact = require('react');
  const { View: TestView } = require('react-native');
  return {
    FixturePreviewApp: (props: Record<string, unknown>) =>
      TestReact.createElement(TestView, {
        ...props,
        testID: 'authorized-fixture-root',
      }),
  };
});

afterEach(async () => {
  await cleanup();
});

describe('DeviceE2EFixtureApp host authorization', () => {
  it('rejects missing or malformed native host properties', async () => {
    await render(<DeviceE2EFixtureApp e2ePlatform="android" />);
    expect(screen.getByTestId('e2e-host-rejected')).toBeTruthy();
    expect(screen.queryByTestId('authorized-fixture-root')).toBeNull();
  });

  it('forwards only bounded properties from the compile-time fixture host', async () => {
    await render(
      <DeviceE2EFixtureApp
        e2eLanguage="hi"
        e2ePlatform="ios"
        e2eRuntimeToken="birthday-e2e-fixture-v1"
        e2eSetupComplete
      />,
    );
    const fixture = screen.getByTestId('authorized-fixture-root');
    expect(fixture.props.initialLanguage).toBe('hi');
    expect(fixture.props.initialSetupComplete).toBe(true);
    expect(fixture.props.platformOverride).toBe('ios');
  });
});
