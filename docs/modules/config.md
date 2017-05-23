# Remote Config

?> Remote Config is not available on the web SDK. Please read the [documentation](https://firebase.google.com/docs/remote-config) on when & why Remote Config benefits your app.

## API

### enableDeveloperMode()

Enable Remote Config developer mode to allow for frequent refreshes of the cache.

```js
firebase.config().enableDeveloperMode();
```

### setDefaults(defaults: `Object`)

Sets default values for the app to use when accessing values. Any data fetched and activated will override any default values.
Any values in the defaults but not on Firebase will be untouched.

```js
 firebase.config().setDefaults({
   hasExperimentalFeature: false,
 });
```

### fetch(): `Promise<String>`

Fetches the remote config data from Firebase, defined in the dashboard. To have fetched data available in the active config, use
`activateFetched`.

Thrown errors can be one of the following:
* config/failure - Config fetch failed.
* config/no_fetch_yet - Config has never been fetched.
* config/throttled - Config fetch was throttled.

```js
 firebase.config()
   .fetch()
   .then(() => {
     console.log('Successfully fetched data');
   })
   .catch((error) => {
     console.error('Unable to fetch remote config data: ', error);
   });
```

### fetchWithExpirationDuration(duration: `int`): `Promise<String>`

Same as `fetch`, however defines (in seconds) how long fetched data is available in the active config.

Thrown errors can be one of the following:
* config/failure - Config fetch failed.
* config/no_fetch_yet - Config has never been fetched.
* config/throttled - Config fetch was throttled.

Default duration is 12 hours (43200 seconds):


```js
 firebase.config()
   .fetchWithExpirationDuration(1337)
   .then(() => {
     console.log('Successfully fetched data for 1337 seconds');
   })
   .catch((error) => {
     console.error('Unable to fetch remote config data: ', error);
   });
```

### activateFetched(): `Promise<Boolean>`

Moves fetched data in the apps active config. Always successfully resolves with a boolean value.

```js
 firebase.config()
   .activateFetched()
   .then((success) => {
     if (success) {
       console.log('Successfully activated fetched config into active config');
     } else {
       console.log('No fetched config was found or fetched config is already active');
     }
   });
```

### getValue(key: `String`): `Promise<Object>`

Gets a config item by key. Returns an object containing source (default, remote or static) and val function.

```js
 firebase.config()
   .getValue('foobar')
   .then((snapshot) => {
     console.log('Got value from source: ', snapshot.source);
     console.log('Value of foobar: ', snapshot.val());
   })
   .catch(console.error);
```

### getValues(keys: `Array<String>`): `Promise<Object>`

Gets multiple values by key. Returns an object of keys with the same object returned from `getValue`.

```js
 firebase.config()
   .getValues(['foobar', 'barbaz'])
   .then((snapshot) => {
     console.log('Value of foobar: ', snapshot.foobar.val());
     console.log('Value of barbaz: ', snapshot.barbaz.val());
   })
   .catch(console.error);
```

## Usage

```js
if (__DEV__) {
  firebase.config().enableDeveloperMode();
}

// Set default values
firebase.config().setDefaults({
  hasExperimentalFeature: false,
});

firebase.remoteConfig.fetch()
  .then(() => {
    return firebase.remoteConfig().activateFetched();
  })
  .then((activated) => {
    if (!activated) console.log('Fetched data not activated');
    return firebase.remoteConfig().getValue('hasExperimentalFeature');
  })
  .then((snapshot) => {
    const hasExperimentalFeature = snapshot.val();

    if(hasExperimentalFeature) {
      // enableSuperCoolFeature();
    }

    // continue booting app
  })
  .catch(console.error);
```
