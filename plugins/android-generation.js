const fs = require('fs');
const path = require('path');

const normalizeGeneratedContents = contents => `${String(contents).replace(/\r\n/g, '\n').replace(/\n*$/, '')}\n`;

const resolveAndroidPackage = (config, featureName) => {
  const androidPackage = config.android?.package ?? config.modRequest?.config?.android?.package;
  if (!androidPackage) {
    throw new Error(`${featureName} requires expo.android.package.`);
  }
  return androidPackage;
};

const writeGeneratedFileAsync = async (filePath, contents) => {
  const normalizedContents = normalizeGeneratedContents(contents);
  let existingContents;

  try {
    existingContents = await fs.promises.readFile(filePath, 'utf8');
  } catch (error) {
    if (error.code !== 'ENOENT') {
      throw error;
    }
  }

  if (existingContents === normalizedContents) {
    return false;
  }

  await fs.promises.mkdir(path.dirname(filePath), { recursive: true });
  const temporaryPath = path.join(path.dirname(filePath), `.${path.basename(filePath)}.${process.pid}.tmp`);

  try {
    await fs.promises.writeFile(temporaryPath, normalizedContents, 'utf8');
    await fs.promises.rename(temporaryPath, filePath);
  } catch (error) {
    await fs.promises.rm(temporaryPath, { force: true });
    throw error;
  }

  return true;
};

const assertCondition = (condition, message) => {
  if (!condition) {
    throw new Error(message);
  }
};

const countOccurrences = (contents, value) => contents.split(value).length - 1;

module.exports = {
  assertCondition,
  countOccurrences,
  normalizeGeneratedContents,
  resolveAndroidPackage,
  writeGeneratedFileAsync
};
