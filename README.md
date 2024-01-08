WannaGo App
- Home fragment has a logo, short description of the app and contains a link to Places fragment.
- App uses navigation drawer layout for navigation.
- Places Fragment has a sign in button. Each user's location is updated in the top level collection in firestore.
- Each user on selecting a particular place in the map, the recycler view will be updated with the latitude, longitude and name of the location.
- On clicking a particular list item, will move to DetailFragment. It contains a map that shows the location selected and the corresponding latitude and longitude.
- Park fragment stores the locations in a seperate firestore collection and displays the same in recycler view. 
- Users can "swipe to delete" the locations from the recycler view. That deletes the entry from the firestore aswell.
