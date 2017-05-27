import { NativeModules, NativeEventEmitter } from 'react-native';
import { nativeSDKMissing } from './../../utils';

import Interstitial from './Interstitial';
import RewardedVideo from './RewardedVideo';
import AdRequest from './AdRequest';
import Banner from './Banner';
import { Base } from './../base';

const FirebaseAdMob = NativeModules.RNFirebaseAdMob;
const FirebaseAdMobEvt = new NativeEventEmitter(FirebaseAdMob);

export default class Admob extends Base {

  constructor() {
    super();
    if (FirebaseAdMob.nativeSDKMissing) {
      return nativeSDKMissing('admob');
    }

    FirebaseAdMobEvt.addListener('interstitial_event', this._onInterstitialEvent.bind(this));
    FirebaseAdMobEvt.addListener('rewarded_video_event', this._onRewardedVideoEvent.bind(this));
  }

  _onInterstitialEvent(event) {
    const { adUnit } = event;
    const jsEventType = `interstitial_${adUnit}`;

    if (!this.hasListeners(jsEventType)) {
      // TODO
    }

    this.emit(jsEventType, event);
  }

  _onRewardedVideoEvent(event) {
    const { adUnit } = event;
    const jsEventType = `rewarded_video_${adUnit}`;

    if (!this.hasListeners(jsEventType)) {
      // TODO
    }

    this.emit(jsEventType, event);
  }

  interstitial(adUnit: string) {
    return new Interstitial(this, adUnit);
  }

  rewarded(adUnit: string) {
    return new RewardedVideo(this, adUnit);
  }

  static get statics() {
    return statics;
  }
}

export const statics = {
  Banner,
  AdRequest,
  EventTypes: {
    onAdLoaded: 'onAdLoaded',
    onAdOpened: 'onAdOpened',
    onAdLeftApplication: 'onAdLeftApplication',
    onAdClosed: 'onAdClosed',
    onAdFailedToLoad: 'onAdFailedToLoad',
  },
};
