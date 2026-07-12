import React from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

export type IconName =
  | 'home'
  | 'people'
  | 'settings'
  | 'shield'
  | 'check'
  | 'warning'
  | 'info'
  | 'clock'
  | 'activity'
  | 'message'
  | 'pause'
  | 'play'
  | 'bell'
  | 'lock'
  | 'chevron'
  | 'search'
  | 'person';

type IconProps = {
  name: IconName;
  color: string;
  size?: number;
  mirrored?: boolean;
};

const paths: Record<Exclude<IconName, 'info'>, string> = {
  home: 'M3 10.8 12 3l9 7.8V21h-6v-6H9v6H3V10.8Z',
  people:
    'M8.2 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm7.6-1a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4ZM1.5 21v-2.1c0-3.1 3-5.4 6.7-5.4s6.8 2.3 6.8 5.4V21H1.5Zm14.7 0v-2.1c0-1.6-.6-3.1-1.7-4.2.5-.1 1-.2 1.6-.2 3.4 0 6.4 2 6.4 5V21h-6.3Z',
  settings:
    'm20.5 13.2-2 .7a7 7 0 0 1-.7 1.7l.9 1.9-2.2 2.2-1.9-.9a7 7 0 0 1-1.7.7l-.7 2h-3l-.7-2a7 7 0 0 1-1.7-.7l-1.9.9-2.2-2.2.9-1.9a7 7 0 0 1-.7-1.7l-2-.7v-3l2-.7a7 7 0 0 1 .7-1.7l-.9-1.9 2.2-2.2 1.9.9a7 7 0 0 1 1.7-.7l.7-2h3l.7 2a7 7 0 0 1 1.7.7l1.9-.9 2.2 2.2-.9 1.9a7 7 0 0 1 .7 1.7l2 .7v3ZM10.7 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z',
  shield:
    'M12 2 4 5.5v5.3c0 5.1 3.4 9.8 8 11.2 4.6-1.4 8-6.1 8-11.2V5.5L12 2Zm0 3 5 2.2v3.6c0 3.4-2.1 6.8-5 8.1-2.9-1.3-5-4.7-5-8.1V7.2L12 5Z',
  check: 'm9.2 17.1-4.3-4.3 2.1-2.1 2.2 2.2 7.8-7.8 2.1 2.1-9.9 9.9Z',
  warning:
    'M12 2.8 1.4 21h21.2L12 2.8Zm0 5.4 1 7h-2l1-7Zm0 10.2a1.3 1.3 0 1 1 0-2.6 1.3 1.3 0 0 1 0 2.6Z',
  clock:
    'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 5v4.6l3.8 2.2-1 1.7-4.8-2.9V7h2Z',
  activity: 'M4 3h16v18H4V3Zm3 4v2h10V7H7Zm0 4v2h7v-2H7Zm0 4v2h9v-2H7Z',
  message: 'M3 3h18v14H8l-5 4V3Zm3 4v2h12V7H6Zm0 4v2h9v-2H6Z',
  pause: 'M6 4h5v16H6V4Zm7 0h5v16h-5V4Z',
  play: 'm7 4 13 8-13 8V4Z',
  bell: 'M12 22a2.5 2.5 0 0 0 2.4-2h-4.8a2.5 2.5 0 0 0 2.4 2Zm7-5H5l2-2.5V10a5 5 0 0 1 4-4.9V3h2v2.1A5 5 0 0 1 17 10v4.5l2 2.5Z',
  lock: 'M6 10V7a6 6 0 0 1 12 0v3h2v12H4V10h2Zm3 0h6V7a3 3 0 0 0-6 0v3Z',
  chevron: 'm9 5 7 7-7 7-2-2 5-5-5-5 2-2Z',
  search:
    'M10.5 3a7.5 7.5 0 1 0 4.7 13.3L20.9 22l2.1-2.1-5.7-5.7A7.5 7.5 0 0 0 10.5 3Zm0 3a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9Z',
  person:
    'M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-5 0-9 2.5-9 6v2h18v-2c0-3.5-4-6-9-6Z',
};

export function Icon({ name, color, size = 24, mirrored = false }: IconProps) {
  return (
    <Svg
      accessible={false}
      focusable={false}
      width={size}
      height={size}
      viewBox="0 0 24 24"
    >
      {name === 'info' ? (
        <>
          <Circle cx="12" cy="12" r="10" fill={color} />
          <Path fill="#FFFFFF" d="M11 10h2v7h-2v-7Zm0-4h2v2h-2V6Z" />
        </>
      ) : (
        <Path
          fill={color}
          d={paths[name]}
          transform={mirrored ? 'translate(24 0) scale(-1 1)' : undefined}
        />
      )}
    </Svg>
  );
}
