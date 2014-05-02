default_target: deb

deb:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/hadoop/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ hadoop.deb

