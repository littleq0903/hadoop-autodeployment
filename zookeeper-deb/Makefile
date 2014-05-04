default_target: deb
deb: zookeeper.deb
all: deb

zookeeper.deb: md5sums conffiles
	dpkg-deb --build  src/ zookeeper.deb

md5sums:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums

conffiles:
	cd src && find etc/zookeeper/ -type f -printf '/%p\n' > DEBIAN/conffiles

clean:
	rm zookeeper.deb
